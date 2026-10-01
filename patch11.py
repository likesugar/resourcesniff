# 1) NativePlayerActivity: onCreate 包 try-catch，崩溃写 crash.txt + 弹窗
p='/home/z/my-project/xgjhome/app/src/main/java/com/wink/xgjhome/NativePlayerActivity.java'
s=open(p).read()
old='''    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        url [SYSTEM_NOTE: Content compressed. Read the full version if needed.]zable(false)
                .setMessage(android.util.Log.getStackTraceString(e))
                .setPositiveButton("好", null)
                .show();
    }
}'''
assert old in s, 'np anchor'
s=s.replace(old,new,1)
open(p,'w').write(s)
print('np ok')

# 2) HomeActivity: 重装全局崩溃落盘
p2='/home/z/my-project/xgjhome/app/src/main/java/com/wink/xgjhome/HomeActivity.java'
s2=open(p2).read()
if 'setDefaultUncaughtExceptionHandler' not in s2:
    old2='''    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);'''
    new2='''    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getFilesDir();
                    java.io.File f = new java.io.File(dir, "crash.txt");
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.append("\\n==== " + new java.util.Date().toString() + " thread=" + t.getName() + " ====\\n");
                    fw.append(android.util.Log.getStackTraceString(e));
                    fw.close();
                } catch (Throwable e2) { }
                Thread.setDefaultUncaughtExceptionHandler(null);
                throw new RuntimeException(e);
            }
        });'''
    assert old2 in s2, 'home anchor'
    s2=s2.replace(old2,new2,1)
    open(p2,'w').write(s2)
    print('home ok')
