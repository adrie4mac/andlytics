package com.github.andlyticsproject.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.github.andlyticsproject.Constants;
import com.github.andlyticsproject.Preferences;
import com.github.andlyticsproject.model.DeveloperAccount;

public class AndlyticsDb extends SQLiteOpenHelper {

	private static final String TAG = AndlyticsDb.class.getSimpleName();

	private static final int DATABASE_VERSION = 19;

	private static final String DATABASE_NAME = "andlytics";

	private static AndlyticsDb instance;

	private Context context;

	public static synchronized AndlyticsDb getInstance(Context context) {
		if (instance == null) {
			instance = new AndlyticsDb(context);
		}

		return instance;
	}

	private AndlyticsDb(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.context = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "Creating databse");
		db.execSQL(AppInfoTable.TABLE_CREATE_APPINFO);
		db.execSQL(AppStatsTable.TABLE_CREATE_STATS);
		db.execSQL(CommentsTable.TABLE_CREATE_COMMENTS);
		db.execSQL(AdmobTable.TABLE_CREATE_ADMOB);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ".");
		if (oldVersion < 9) {
			Log.w(TAG, "Old version < 9 - drop all tables & recreate");
			db.execSQL("DROP TABLE IF EXISTS " + AppInfoTable.DATABASE_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + AppStatsTable.DATABASE_TABLE_NAME);
			db.execSQL(AppInfoTable.TABLE_CREATE_APPINFO);
			db.execSQL(AppStatsTable.TABLE_CREATE_STATS);
		}

		if (oldVersion == 9) {
			Log.w(TAG, "Old version = 9 - add ghost column");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_GHOST + " integer");
		}
		if (oldVersion > 8 && oldVersion < 11) {
			Log.w(TAG, "Old version = 10 or 9 - add rating expand column");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_RATINGS_EXPANDED + " integer");
		}
		if (oldVersion < 12) {
			Log.w(TAG, "Old version < 12 - add comments table");
			db.execSQL(CommentsTable.TABLE_CREATE_COMMENTS);
		}
		if (oldVersion < 13) {
			Log.w(TAG, "Old version < 13 - add skip notification");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_SKIP_NOTIFICATION + " integer");
		}
		if (oldVersion < 14) {
			Log.w(TAG, "Old version < 14 - add admob table");
			db.execSQL(AdmobTable.TABLE_CREATE_ADMOB);
		}
		if (oldVersion < 15) {
			Log.w(TAG, "Old version < 15 - add version name");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_VERSION_NAME + " text");

		}

		if (oldVersion < 16) {
			Log.w(TAG, "Old version < 16 - add new comments colums");
			db.execSQL("ALTER table " + CommentsTable.DATABASE_TABLE_NAME + " add "
					+ CommentsTable.KEY_COMMENT_APP_VERSION + " text");
			db.execSQL("ALTER table " + CommentsTable.DATABASE_TABLE_NAME + " add "
					+ CommentsTable.KEY_COMMENT_DEVICE + " text");
		}

		if (oldVersion < 17) {
			Log.w(TAG, "Old version < 17 - changing comments date format");
			db.execSQL("DROP TABLE IF EXISTS " + CommentsTable.DATABASE_TABLE_NAME);
			db.execSQL(CommentsTable.TABLE_CREATE_COMMENTS);
		}

		if (oldVersion < 18) {
			Log.w(TAG, "Old version < 18 - adding replies to comments");
			db.execSQL("DROP TABLE IF EXISTS " + CommentsTable.DATABASE_TABLE_NAME);
			db.execSQL(CommentsTable.TABLE_CREATE_COMMENTS);
		}

		if (oldVersion < 19) {
			Log.w(TAG, "Old version < 19 - adding developer_accounts table");
			db.execSQL("DROP TABLE IF EXISTS " + DeveloperAccountsTable.DATABASE_TABLE_NAME);
			db.execSQL(DeveloperAccountsTable.TABLE_CREATE_DEVELOPER_ACCOUNT);

			migrateAccountsFromPrefs(db);

			Log.d(TAG, "Old version < 19 - adding new appinfo columns");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_ADMOB_ACCOUNT + " text");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_ADMOB_SITE_ID + " text");
			db.execSQL("ALTER table " + AppInfoTable.DATABASE_TABLE_NAME + " add "
					+ AppInfoTable.KEY_APP_LAST_COMMENTS_UPDATE + " date");

			migrateAppInfoPrefs(db);
		}

	}

	private void migrateAppInfoPrefs(SQLiteDatabase db) {
		Log.d(TAG, "Migrating app info settings from preferences...");
		int migrated = 0;

		db.beginTransaction();
		try {
			Cursor c = null;
			class Pair {
				long id;
				String packageName;
			}
			List<Pair> packages = new ArrayList<Pair>();
			try {
				c = db.query(AppInfoTable.DATABASE_TABLE_NAME, new String[] {
						AppInfoTable.KEY_ROWID, AppInfoTable.KEY_APP_PACKAGENAME }, null, null,
						null, null, "_id asc", null);
				while (c.moveToNext()) {
					Pair p = new Pair();
					p.id = c.getLong(0);
					p.packageName = c.getString(1);
					packages.add(p);
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}
			for (Pair p : packages) {
				Log.d(TAG, "Migrating package: " + p.packageName);
				String admobSiteId = Preferences.getAdmobSiteId(context, p.packageName);
				if (admobSiteId != null) {
					String admobAccount = Preferences.getAdmobAccount(context, admobSiteId);
					ContentValues values = new ContentValues();
					values.put(AppInfoTable.KEY_APP_ADMOB_SITE_ID, admobSiteId);
					values.put(AppInfoTable.KEY_APP_ADMOB_ACCOUNT, admobAccount);
					db.update(AppInfoTable.DATABASE_TABLE_NAME, values, "_id = ?",
							new String[] { Long.toString(p.id) });
				}
				long lastCommentsUpdate = Preferences.getLastCommentsRemoteUpdateTime(context,
						p.packageName);
				if (lastCommentsUpdate != 0) {
					ContentValues values = new ContentValues();
					values.put(AppInfoTable.KEY_APP_LAST_COMMENTS_UPDATE, lastCommentsUpdate);
					db.update(AppInfoTable.DATABASE_TABLE_NAME, values, "_id = ?",
							new String[] { Long.toString(p.id) });
				}
				migrated++;
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Log.d(TAG,
				String.format("Successfully migrated app info settings for %d packages", migrated));
	}

	private void migrateAccountsFromPrefs(SQLiteDatabase db) {
		Log.d(TAG, "Migrating developer accounts from preferences...");
		int migrated = 0;

		db.beginTransaction();
		try {
			AccountManager am = AccountManager.get(context);
			Account[] accounts = am.getAccountsByType(Constants.ACCOUNT_TYPE_GOOGLE);
			String activeAccount = Preferences.getAccountName(context);
			for (Account account : accounts) {
				boolean isHidden = Preferences.getIsHiddenAccount(context, account.name);
				long lastStatsUpdate = Preferences.getLastStatsRemoteUpdateTime(context,
						account.name);
				DeveloperAccount.State state = DeveloperAccount.State.ACTIVE;
				if (isHidden) {
					state = DeveloperAccount.State.HIDDEN;
				}
				if (account.name.equals(activeAccount)) {
					state = DeveloperAccount.State.SELECTED;
				}
				DeveloperAccount developerAccount = new DeveloperAccount(account.name, state);
				if (lastStatsUpdate != 0) {
					developerAccount.setLastStatsUpdate(new Date(lastStatsUpdate));
				}

				Log.d(TAG, "Adding account: " + developerAccount);
				addDeveloperAccount(db, developerAccount);
				migrated++;
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Log.d(TAG, String.format("Successfully migrated %d developer accounts", migrated));
	}

	private long addDeveloperAccount(SQLiteDatabase db, DeveloperAccount account) {
		ContentValues values = toValues(account);

		return db.insertOrThrow(DeveloperAccountsTable.DATABASE_TABLE_NAME, null, values);
	}

	public synchronized long addDeveloperAccount(DeveloperAccount account) {
		return addDeveloperAccount(getWritableDatabase(), account);
	}

	private ContentValues toValues(DeveloperAccount account) {
		ContentValues result = new ContentValues();
		result.put(DeveloperAccountsTable.NAME, account.getName());
		result.put(DeveloperAccountsTable.STATE, account.getState().ordinal());
		if (account.getLastStatsUpdate() != null) {
			result.put(DeveloperAccountsTable.LAST_STATS_UPDATE, account.getLastStatsUpdate()
					.getTime());
		}

		return result;
	}

	public List<DeveloperAccount> getAllDeveloperAccounts() {
		List<DeveloperAccount> result = new ArrayList<DeveloperAccount>();

		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			c = db.query(DeveloperAccountsTable.DATABASE_TABLE_NAME,
					DeveloperAccountsTable.ALL_COLUMNS, null, null, null, null, "_id asc", null);
			while (c.moveToNext()) {
				DeveloperAccount account = createAcount(c);
				result.add(account);
			}

			return result;
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public List<DeveloperAccount> getActiveDeveloperAccounts() {
		return getDeveloperAccountsByState(DeveloperAccount.State.ACTIVE);
	}

	public List<DeveloperAccount> getHiddenDeveloperAccounts() {
		return getDeveloperAccountsByState(DeveloperAccount.State.HIDDEN);
	}

	public DeveloperAccount getSelectedDeveloperAccount() {
		List<DeveloperAccount> accounts = getDeveloperAccountsByState(DeveloperAccount.State.SELECTED);
		if (accounts.isEmpty()) {
			return null;
		}

		if (accounts.size() > 1) {
			throw new IllegalStateException("More than one selected developer account: " + accounts);
		}

		return accounts.get(0);
	}

	public List<DeveloperAccount> getDeveloperAccountsByState(DeveloperAccount.State state) {
		List<DeveloperAccount> result = new ArrayList<DeveloperAccount>();

		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			c = db.query(DeveloperAccountsTable.DATABASE_TABLE_NAME,
					DeveloperAccountsTable.ALL_COLUMNS, "state = ?",
					new String[] { Integer.toString(state.ordinal()) }, null, null, "_id asc", null);
			while (c.moveToNext()) {
				DeveloperAccount account = createAcount(c);
				result.add(account);
			}

			return result;
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public DeveloperAccount findAccountById(long id) {
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			c = db.query(DeveloperAccountsTable.DATABASE_TABLE_NAME,
					DeveloperAccountsTable.ALL_COLUMNS, "_id = ?",
					new String[] { Long.toString(id) }, null, null, "_id asc", null);
			if (!c.moveToNext()) {
				return null;
			}

			return createAcount(c);
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public DeveloperAccount findAccountByName(String name) {
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = null;
		try {
			c = db.query(DeveloperAccountsTable.DATABASE_TABLE_NAME,
					DeveloperAccountsTable.ALL_COLUMNS, "name = ?", new String[] { name }, null,
					null, "_id asc", null);
			if (!c.moveToNext()) {
				return null;
			}

			return createAcount(c);
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public synchronized void updateDeveloperAccount(DeveloperAccount account) {
		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = toValues(account);
		values.put(DeveloperAccountsTable.ROWID, account.getId());

		db.update(DeveloperAccountsTable.DATABASE_TABLE_NAME, values, "_id = ?",
				new String[] { Long.toString(account.getId()) });
	}

	private DeveloperAccount createAcount(Cursor c) {
		long id = c.getLong(c.getColumnIndex(DeveloperAccountsTable.ROWID));
		String name = c.getString(c.getColumnIndex(DeveloperAccountsTable.NAME));
		DeveloperAccount.State currentState = DeveloperAccount.State.values()[c.getInt(c
				.getColumnIndex(DeveloperAccountsTable.STATE))];
		Date lastStatsUpdate = new Date(c.getLong(c
				.getColumnIndex(DeveloperAccountsTable.LAST_STATS_UPDATE)));

		DeveloperAccount account = new DeveloperAccount(name, currentState);
		account.setId(id);
		account.setLastStatsUpdate(lastStatsUpdate);
		return account;
	}
}