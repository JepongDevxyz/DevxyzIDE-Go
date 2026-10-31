package rikka.shizuku.server;

import com.itsaky.androidide.buildinfo.BuildInfo;

public class ServerConstants {

	public static final int MANAGER_APP_NOT_FOUND = 50;

	public static final String MANAGER_APPLICATION_ID = BuildInfo.APPLICATION_ID;
	public static final String PERMISSION = MANAGER_APPLICATION_ID + ".shizuku.permission.SHIZUKU_API_V23";
	public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".shizuku.intent.action.REQUEST_PERMISSION";

}
