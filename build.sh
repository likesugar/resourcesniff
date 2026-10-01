#!/bin/bash
# 资源嗅探（com.wink.xgjhome）云构建：aapt2 + ecj + d8 + zipalign + apksigner（纯框架API，无三方依赖）
set -e
VC=${VC:-5}
VN=${VN:-"3.0"}

WORK=$HOME/work
mkdir -p $WORK && cd $WORK

BT=$ANDROID_HOME/build-tools/34.0.0
SDK=$ANDROID_HOME/platforms/android-34/android.jar
PROJ=$GITHUB_WORKSPACE

rm -rf build_out && mkdir -p build_out/gen build_out/classes build_out/dex

echo "[1/6] aapt2 compile..."
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o build_out/res.zip

echo "[2/6] aapt2 link..."
"$BT/aapt2" link -o build_out/app-unsigned.apk \
    -I "$SDK" \
    --manifest "$PROJ/app/src/main/AndroidManifest.xml" \
    -R build_out/res.zip \
    --java build_out/gen --auto-add-overlay \
    --min-sdk-version 29 --target-sdk-version 36 \
    --version-code "$VC" --version-name "$VN"

echo "[3/6] ecj compile..."
RJ=$(find build_out/gen -name R.java)
curl -sL -o ecj.jar "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar"
java -jar ecj.jar -source 1.8 -target 1.8 -encoding UTF-8 -proc:none -nowarn \
    -cp "$SDK" -d build_out/classes \
    "$RJ" \
    "$PROJ"/app/src/main/java/com/wink/xgjhome/*.java \
    $PROJ/app/src/main/java/xyz/doikki/videoplayer/*/*.java \
    $PROJ/app/src/main/java/xyz/doikki/videocontroller/*.java \
    $PROJ/app/src/main/java/xyz/doikki/videocontroller/component/*.java

echo "[4/6] d8 dex..."
find build_out/classes -name "*.class" > build_out/classlist.txt
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release \
    --lib "$SDK" --min-api 29 --output build_out/dex \
    @build_out/classlist.txt

echo "[5/6] dex + zipalign..."
for d in build_out/dex/*.dex; do
    (cd build_out/dex && zip -q ../app-unsigned.apk "$(basename $d)")
done
"$BT/zipalign" -f 4 build_out/app-unsigned.apk build_out/app-aligned.apk

echo "[6/6] sign + verify..."
"$BT/apksigner" sign --ks "$PROJ/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$PROJ/资源嗅探_v$VN.apk" build_out/app-aligned.apk
"$BT/apksigner" verify "$PROJ/资源嗅探_v$VN.apk"

echo "=== DONE: 资源嗅探_v$VN.apk ==="
