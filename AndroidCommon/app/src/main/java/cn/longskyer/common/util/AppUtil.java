package cn.longskyer.common.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AppUtil {

    public static Application application;//全局唯一的application

    public static ActivityLifecycle activityLifecycle = new ActivityLifecycle();//activity生命周期，实现了Application.ActivityLifecycleCallbacks

    /**
     * 获取packetInfo
     * @param context
     * @return
     */
    private static PackageInfo getPackageInfo(Context context) {
        PackageInfo pi = null;

        try {
            PackageManager pm = context.getPackageManager();
            pi = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_CONFIGURATIONS);

            return pi;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return pi;
    }

    /**
     * 获取应用版本名称
     * @param context
     * @return
     */
    public static String getVersionName(Context context) {
        return getPackageInfo(context).versionName;
    }

    /**
     * 获取应用版本名称
     * @param context
     * @return
     */
    public static int getVersionCode(Context context) {
        return getPackageInfo(context).versionCode;
    }

    /**
     * 初始化application实例
     * @param app
     */
    public static void init(@NonNull Application app) {
        if (application == null) {
            AppUtil.application = app;
            AppUtil.application.registerActivityLifecycleCallbacks(activityLifecycle);
        }
    }

    /**
     * 根据context初始化application实例
     * @param context
     */
    public static void init(@NonNull Context context) {
        init((Application) context.getApplicationContext());
    }

    public static Application getApp() {
        if (application != null) return application;
        //根据反射获取application实例
        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object at = activityThread.getMethod("currentActivityThread").invoke(null);
            Object app = activityThread.getMethod("getApplication").invoke(at);
            if (app == null) {
                throw new NullPointerException("application没有初始化");
            }
            init((Application) app);
            return application;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        throw new NullPointerException("application没有初始化");
    }

    /**
     * 自定义ActivityLifecycle实现接口Application.ActivityLifecycleCallbacks
     */
    public static class ActivityLifecycle implements Application.ActivityLifecycleCallbacks {

        final LinkedList<Activity> activityList      = new LinkedList<>();
        final Map<Object, OnAppStatusChangedListener> statusListenerMap = new HashMap<>();

        private int foregroundCount = 0;//处于前台运行状态的activity个数
        private int configCount     = 0;


        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            setTopActivity(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            setTopActivity(activity);
            if (foregroundCount <= 0) {
                postStatus(true);
            }
            if (configCount < 0) {
                ++configCount;
            } else {
                ++foregroundCount;
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            setTopActivity(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {/**/}

        @Override
        public void onActivityStopped(Activity activity) {
            if (activity.isChangingConfigurations()) {
                --configCount;
            } else {
                --foregroundCount;
                if (foregroundCount <= 0) {
                    postStatus(false);
                }
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {/**/}

        @Override
        public void onActivityDestroyed(Activity activity) {
            activityList.remove(activity);
        }

        void addListener(final Object object, final OnAppStatusChangedListener listener) {
            statusListenerMap.put(object, listener);
        }

        void removeListener(final Object object) {
            statusListenerMap.remove(object);
        }

        /**
         * 设置app前后台状态
         * @param isForeground
         */
        private void postStatus(boolean isForeground) {
            if (statusListenerMap.isEmpty()) return;
            for (OnAppStatusChangedListener onAppStatusChangedListener : statusListenerMap.values()) {
                if (onAppStatusChangedListener == null) return;
                if (isForeground) {
                    onAppStatusChangedListener.onForeground();
                } else {
                    onAppStatusChangedListener.onBackground();
                }
            }
        }

        /**
         * 设置顶端activity
         * @param activity
         */
        private void setTopActivity(Activity activity) {

            if (activityList.contains(activity)) {
                if (!activityList.getLast().equals(activity)) {
                    activityList.remove(activity);
                    activityList.addLast(activity);
                }
            } else {
                activityList.addLast(activity);
            }
        }

        /**
         * 获取activity桟顶端activity
         * @return
         */
        Activity getTopActivity() {
            if (!activityList.isEmpty()) {
                final Activity topActivity = activityList.getLast();
                if (topActivity != null) {
                    return topActivity;
                }
            }
            //使用反射获取top activity
            try {
                @SuppressLint("PrivateApi")
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
                Field activitiesField = activityThreadClass.getDeclaredField("mActivityList");
                activitiesField.setAccessible(true);
                Map activities = (Map) activitiesField.get(activityThread);
                if (activities == null) return null;
                for (Object activityRecord : activities.values()) {
                    Class activityRecordClass = activityRecord.getClass();
                    Field pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = activityRecordClass.getDeclaredField("activity");
                        activityField.setAccessible(true);
                        Activity activity = (Activity) activityField.get(activityRecord);
                        setTopActivity(activity);
                        return activity;
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * 根据app处在前台或者后台运行状态做出不同响应
     */
    public interface OnAppStatusChangedListener {
        void onForeground();

        void onBackground();
    }



}
