p='/home/z/my-project/xgjhome/app/src/main/java/com/wink/xgjhome/SniffActivity.java'
s=open(p).read()

# Cookie 7天过期：过期即删并要求重新登录
old='''        static String storedCookies(android.content.Context c) {
            return c.getSharedPreferences("bili", android.content.Context.MODE_PRIVATE)
           [SYSTEM_NOTE: Content compressed. Read the full version if needed.]ile(dir, "crash.txt");
            java.io.FileWriter fw = new java.io.FileWriter(f, true);
            fw.append("\\n==== " + new java.util.Date().toString() + " ====\\n");
            fw.append(android.util.Log.getStackTraceString(e));
            fw.close();
        } catch (Throwable e2) { }
        new android.app.AlertDialog.Builder(this)'''
assert old in s, 'crash anchor'
s=s.replace(old,new,1)
open(p,'w').write(s)
print('ok')
