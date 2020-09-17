/****************************************************************************
Copyright (c) 2013-2016 Chukong Technologies Inc.
Copyright (c) 2017-2018 Xiamen Yaji Software Co., Ltd.

http://www.cocos2d-x.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
****************************************************************************/
package org.cocos2dx.lib;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.IpSecManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class Cocos2dxLocalStorage {

    public interface SQLTask {
        public abstract void run();
    }

    private static final String TAG = "Cocos2dxLocalStorage";

    private static String DATABASE_NAME = "jsb.sqlite";
    private static String TABLE_NAME = "data";
    private static final int DATABASE_VERSION = 1;

    private static DBOpenHelper mDatabaseOpenHelper = null;
    private static SQLiteDatabase mDatabase = null;

    private static HandlerThread mSQLThread = null;
    private static Handler mLoopHandler = null;
    /**
     * Constructor
     * @param context The Context within which to work, used to create the DB
     * @return 
     */
    public static boolean init(String dbName, String tableName) {
        if (Cocos2dxActivity.getContext() != null) {
            DATABASE_NAME = dbName;
            TABLE_NAME = tableName;
            mDatabaseOpenHelper = new DBOpenHelper(Cocos2dxActivity.getContext());
            mDatabase = mDatabaseOpenHelper.getWritableDatabase();

            mSQLThread = new HandlerThread("SQL Thread");
            mSQLThread.start();
            mLoopHandler = new Handler(mSQLThread.getLooper()){
                @Override
                public void handleMessage(Message msg)
                {
                    // todo: msg should contain a callback
                    // not necessary, use mLoopHandler.pos() directly
                    /*Runnable cb = msg.getCallback();
                    if (cb != null)
                        cb.run();*/
                }
            };

            return true;
        }
        return false;
    }

    protected static void PostSync(Object sem, SQLTask task) {
        mLoopHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        synchronized (sem) {
                            sem.notify();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        try {
            synchronized (sem) {
                sem.wait();
            }
            int k = 0;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected static void PostAsync(SQLTask task) {
        mLoopHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void destroy() {
        final int[] res = {0};
        PostSync(res, new SQLTask() {
            @Override
            public void run() {
                if (mDatabase != null) {
                    mDatabase.close();
                }
            }
        });

        mSQLThread.quit();  // or quitSafely()
    }

    public static void setItem(String key, String value) {
        PostAsync(new SQLTask() {
            @Override
            public void run() {
                String sql = "replace into " + TABLE_NAME + "(key,value)values(?,?)";
                mDatabase.execSQL(sql, new Object[]{key, value});
            }
        });
    }

    public static String getItem(String key) {
        final String[] ret = {null};
        PostSync(ret, new SQLTask() {
            @Override
            public void run() {
                String sql = "select value from "+TABLE_NAME+" where key=?";
                Cursor c = mDatabase.rawQuery(sql, new String[]{key});
                while (c.moveToNext()) {
                    // only return the first value
                    if (ret[0] != null)
                    {
                        Log.e(TAG, "The key contains more than one value.");
                        break;
                    }
                    ret[0] = c.getString(c.getColumnIndex("value"));
                }
                c.close();
            }
        });

        return ret[0];
    }

    public static void removeItem(String key) {
        PostAsync(new SQLTask() {
            @Override
            public void run() {
                String sql = "delete from "+TABLE_NAME+" where key=?";
                mDatabase.execSQL(sql, new Object[] {key});
            }
        });
    }

    public static void clear() {
        PostAsync(new SQLTask() {
            @Override
            public void run() {
                String sql = "delete from " + TABLE_NAME;
                mDatabase.execSQL(sql);
            }
        });
    }

    public static String getKey(int nIndex) {
        final String[] ret = {null};
        PostSync(ret, new SQLTask() {
            @Override
            public void run() {
                int nCount = 0;
                String sql = "select key from "+TABLE_NAME + " order by rowid asc";
                Cursor c = mDatabase.rawQuery(sql, null);
                if(nIndex < 0 || nIndex >= c.getCount()) {
                    // null
                    return;
                }

                while (c.moveToNext()) {
                    if(nCount == nIndex) {
                        ret[0] = c.getString(c.getColumnIndex("key"));
                        break;
                    }
                    nCount++;
                }
                c.close();
            }
        });

        return ret[0];
    }

    public static int getLength() {
        final int[] res = {0};
        PostSync(res, new SQLTask() {
            @Override
            public void run() {
                String sql = "select count(*) as nums from "+TABLE_NAME;
                Cursor c = mDatabase.rawQuery(sql, null);
                if (c.moveToNext()){
                    res[0] = c.getInt(c.getColumnIndex("nums"));
                }
                c.close();
            }
        });

        return res[0];
    }

    /**
     * This creates/opens the database.
     */
    private static class DBOpenHelper extends SQLiteOpenHelper {

        DBOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS "+TABLE_NAME+"(key TEXT PRIMARY KEY,value TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            //db.execSQL("DROP TABLE IF EXISTS " + VIRTUAL_TABLE);
            //onCreate(db);
        }
    }
}
