package com.hyc.dd_monitor.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final boolean DEBUG = true;

    private static final String PATH = Environment.getExternalStorageDirectory().getPath() + "/DDPlayerLog/";

    private static final String FILE_NAME = "crash";
    private static final String FILE_NAME_SUFFIX = ".txt";

    private static CrashHandler sInstance = new CrashHandler();

    private Context mContext;

    //私有构造器
    private CrashHandler() {

    }

    //单例模式
    public static CrashHandler getInstance() {

        return sInstance;
    }

    //初始化
    public void init(Context context) {

        Thread.setDefaultUncaughtExceptionHandler(this);
        mContext = context;

    }

    /**
     * 重写uncaughtException
     * @param t 发生Crash的线程
     * @param ex Throwale对象
     */
    @Override
    public void uncaughtException(Thread t, Throwable ex) {
        //处理逻辑需要开启一个子线程，用于文件的写入操作
        handleException(ex);
        //在程序关闭之前休眠2秒，以避免在文件写入的操作完
        //成。之前进程被杀死。
        //也可以考虑弹出对话框友好提示用户
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.exit(0);
//        Process.killProcess(Process.myPid());
    }

    //处理异常
    private void handleException(final Throwable ex) {
        try {
            Executors.newSingleThreadExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    dumpExceptionToSDCard(ex);
                    uploadExceptionToServer();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 将异常信息上传至服务器
     */
    private void uploadExceptionToServer() {


    }

    /**
     * 将异常信息写入sd卡
     * @param ex
     */
    private void dumpExceptionToSDCard(Throwable ex) {
        //判断是否支持SD卡
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            if (DEBUG) {
                Log.i(TAG, "sdcard unfind ,skip dump exception");
                return;
            }
        }

        File dir = new File(PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        long current = System.currentTimeMillis();
        String time = new SimpleDateFormat("yyyy-MM-dd").format(new Date(current));

        File file = new File(PATH + FILE_NAME + time + FILE_NAME_SUFFIX);

        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));

            pw.println(time);
            dumpPhoneInfo(pw);
            pw.println();
            //将抛出的异常信息写入到文件
            pw.println(ex.getMessage());
            ex.printStackTrace(pw);
            pw.close();
        } catch (Exception e) {
            Log.d(TAG, "dump Exception Exception" + e.getMessage());
            e.printStackTrace();
        }

    }


    /**
     *
     * 获取手机信息
     * @param pw 写入流
     * @throws PackageManager.NameNotFoundException 异常
     */
    private void dumpPhoneInfo(PrintWriter pw) throws PackageManager.NameNotFoundException {

        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);

        pw.print("App Version : ");
        pw.print(pi.versionName);
        Log.d(TAG,"name : "+pi.versionName);
        pw.print('_');
        pw.println(pi.versionCode);

        pw.print("OS Version : ");
        pw.print(Build.VERSION.RELEASE);
        pw.print("_");
        pw.println(Build.VERSION.SDK_INT );

        pw.print("Vendor : ");
        pw.println(Build.MANUFACTURER);

        pw.print("Model : ");
        pw.println(Build.MODEL);
        pw.print("Cpu ABI : ");
        pw.println(Build.CPU_ABI);
    }

}