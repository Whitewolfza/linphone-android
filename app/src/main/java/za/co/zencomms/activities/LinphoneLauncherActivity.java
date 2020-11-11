/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package za.co.zencomms.activities;

import static za.co.clevercom.framework.Core.ShowErrorMessage;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.linphone.R;
import org.linphone.core.AVPFMode;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.NatPolicy;
import org.linphone.core.PayloadType;
import org.linphone.core.ProxyConfig;
import org.linphone.core.TransportType;
import za.co.clevercom.Data.Functions;
import za.co.clevercom.Data.PostgreSQL.Query;
import za.co.clevercom.Data.TaskDelegate;
import za.co.zencomms.LinphoneContext;
import za.co.zencomms.LinphoneManager;
import za.co.zencomms.assistant.MenuAssistantActivity;
import za.co.zencomms.chat.ChatActivity;
import za.co.zencomms.contacts.ContactsActivity;
import za.co.zencomms.dialer.DialerActivity;
import za.co.zencomms.history.HistoryActivity;
import za.co.zencomms.service.LinphoneService;
import za.co.zencomms.service.ServiceWaitThread;
import za.co.zencomms.service.ServiceWaitThreadListener;
import za.co.zencomms.settings.LinphonePreferences;

/** Creates LinphoneService and wait until Core is ready to start main Activity */
public class LinphoneLauncherActivity extends LinphoneGenericActivity
        implements ServiceWaitThreadListener {
    private static LinphonePreferences mPrefs;
    private static ProxyConfig mProxyConfig;
    private static AuthInfo mAuthInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (!getResources().getBoolean(R.bool.use_full_screen_image_splashscreen)) {
            setContentView(R.layout.launch_screen);
        } // Otherwise use drawable/launch_screen layer list up until first activity starts
    }

    @Override
    protected void onStart() {
        super.onStart();
        //        int readPhoneState =
        //                getPackageManager()
        //                        .checkPermission(Manifest.permission.READ_PHONE_STATE,
        // getPackageName());
        //        if (readPhoneState != PackageManager.PERMISSION_GRANTED) {
        //            ActivityCompat.requestPermissions(
        //                    this, new String[] {Manifest.permission.READ_PHONE_STATE}, 0);
        //        }
        Core core = LinphoneManager.getCore();
        core.clearAllAuthInfo();
        core.clearProxyConfig();
        mPrefs = LinphonePreferences.instance();
        Functions.parent = this;
        if (LinphoneService.isReady()) {
            onServiceReady();
        } else {
            try {
                startService(
                        new Intent()
                                .setClass(LinphoneLauncherActivity.this, LinphoneService.class));
                new ServiceWaitThread(this).start();
            } catch (IllegalStateException ise) {
                Log.e("Linphone", "Exception raised while starting service: " + ise);
            }
        }
    }

    private void CheckUser() {
        CheckUserAsc check = new CheckUserAsc(delegate, this);
        check.execute();
    }

    private static class CheckUserAsc extends AsyncTask<Void, Void, String> {
        private TaskDelegate delegate;
        Context context;

        CheckUserAsc(TaskDelegate delegate, Context context) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        protected void onPreExecute() {}

        @Override
        protected String doInBackground(Void... params) {
            //            long cleintid = Long.valueOf(activity.getString(R.string.clientid));

            // Check User Details

            try {
                long cleintid = Long.parseLong(((Activity) context).getString(R.string.clientid));
                String DeviceID = Functions.GetIMEI(); // Functions.GetDeviceSerial(context); //
                String PublicIP = Functions.getPublicIPAddress();
                Query query2 =
                        new Query(
                                "check_user",
                                ((Activity) context).getString(R.string.DBconnS),
                                ((Activity) context).getString(R.string.DBUser),
                                ((Activity) context).getString(R.string.DBPass),
                                4);
                query2.AddVarchar(1, DeviceID);
                query2.AddBigInt(2, cleintid);
                query2.AddBigInt(3, 0);
                query2.AddVarchar(4, PublicIP);
                ResultSet user = query2.ExecData();
                user.next();

                Core core = LinphoneManager.getCore();

                int sipPort = 5076;

                if (mProxyConfig == null) {
                    // Ensure the default configuration is loaded first
                    String defaultConfig =
                            LinphonePreferences.instance().getDefaultDynamicConfigFile();
                    core.loadConfigFromXml(defaultConfig);
                    mProxyConfig = core.createProxyConfig();
                    if (user.getString("sipuser").equals("unknown")) {
                        mAuthInfo =
                                Factory.instance()
                                        .createAuthInfo(null, null, null, null, null, null);
                        return "Pending";
                    } else {
                        mAuthInfo =
                                Factory.instance()
                                        .createAuthInfo(
                                                user.getString("sipuser"),
                                                user.getString("sipuser"),
                                                user.getString("sippass"),
                                                null,
                                                user.getString("pbx"),
                                                user.getString("pbx")
                                                        + ":"
                                                        + String.valueOf(sipPort));
                    }
                } else {
                    Address identityAddress = mProxyConfig.getIdentityAddress();
                    mAuthInfo = mProxyConfig.findAuthInfo();

                    NatPolicy natPolicy = mProxyConfig.getNatPolicy();
                    if (natPolicy == null) {
                        natPolicy = core.createNatPolicy();
                        core.setNatPolicy(natPolicy);
                    }
                }

                if (mAuthInfo == null
                        || mAuthInfo.getUsername() == null
                        || mAuthInfo.getUsername().equals("")) {
                    return "Pending";
                }

                SetAccount(user);

            } catch (Exception e) {
                LoginStatus = "Pending";
                e.printStackTrace();
                return e.getMessage();
            }

            return "Done";
        }

        @Override
        protected void onPostExecute(String value) {
            delegate.TaskCompletionResult(value);
        }
    }

    private static class GetUser extends AsyncTask<Void, Void, String> {
        private TaskDelegate delegate;
        Context context;
        Activity activity;
        String ext;

        GetUser(TaskDelegate delegate, Context context, String ext) {
            this.delegate = delegate;
            this.context = context;
            this.activity = (Activity) context;
            this.ext = ext;
        }

        @Override
        protected void onPreExecute() {
            Activity activity = (Activity) context;
        }

        @Override
        protected String doInBackground(Void... params) {
            long cleintid = Long.valueOf(activity.getString(R.string.clientid));
            String PublicIP = Functions.getPublicIPAddress();

            try {
                String DeviceID = Functions.GetIMEI(); // Functions.GetDeviceSerial(context); //
                Query query2 =
                        new Query(
                                "register_login_device",
                                activity.getString(R.string.DBconnS),
                                activity.getString(R.string.DBUser),
                                activity.getString(R.string.DBPass),
                                5);
                query2.AddVarchar(1, DeviceID);
                query2.AddVarchar(2, ext);
                query2.AddBigInt(3, cleintid);
                query2.AddBigInt(4, 0);
                query2.AddVarchar(5, PublicIP);
                ResultSet user = query2.ExecData();
                user.next();

                SetAccount(user);

                return "Done";

            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String value) {
            delegate.TaskCompletionResult(value);
        }
    }

    static String LoginStatus = "";

    private TaskDelegate delegate =
            new TaskDelegate() {
                @Override
                public void TaskCompletionResult(String o) {
                    switch (o) {
                        case "Pending":
                            LoginStatus = "Pending";
                            showLogin();
                            break;
                        case "Done":
                            LoginStatus = "Done";
                            SettingsSaved();
                            break;
                        default:
                            if (LoginStatus.equals("Pending")) {
                                showLogin();
                            }
                            ShowErrorMessage(o);
                            break;
                    }
                }
            };
    static Context context;

    private void showLogin() {
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.prompt, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        TextView txtTitle = promptsView.findViewById(R.id.txtTitle);
        txtTitle.setText(getString(R.string.prompt_register));

        final EditText userInput =
                (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);

        context = this;

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(
                        "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // get user input and set it to result
                                // edit text
                                runOnUiThread(
                                        new Runnable() {
                                            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                                            @Override
                                            public void run() {
                                                GetUser addNote =
                                                        new GetUser(
                                                                delegate,
                                                                context,
                                                                userInput.getText().toString());
                                                addNote.execute();
                                            }
                                        });
                            }
                        })
                .setNegativeButton(
                        "Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                finish();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();
        // show it
        alertDialog.show();
    }

    private void SettingsSaved() {
        final Class<? extends Activity> classToStart;

        boolean useFirstLoginActivity =
                getResources().getBoolean(R.bool.display_account_assistant_at_first_start);
        if (useFirstLoginActivity && LinphonePreferences.instance().isFirstLaunch()) {
            classToStart = MenuAssistantActivity.class;
        } else {
            if (getIntent().getExtras() != null) {
                String activity = getIntent().getExtras().getString("Activity", null);
                if (ChatActivity.NAME.equals(activity)) {
                    classToStart = ChatActivity.class;
                } else if (HistoryActivity.NAME.equals(activity)) {
                    classToStart = HistoryActivity.class;
                } else if (ContactsActivity.NAME.equals(activity)) {
                    classToStart = ContactsActivity.class;
                } else {
                    classToStart = DialerActivity.class;
                }
            } else {
                classToStart = DialerActivity.class;
            }
        }
        Intent intent = new Intent();
        intent.setClass(LinphoneLauncherActivity.this, classToStart);
        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        intent.setAction(getIntent().getAction());
        intent.setType(getIntent().getType());
        intent.setData(getIntent().getData());
        startActivity(intent);

        LinphoneManager.getInstance().changeStatusToOnline();
    }

    @Override
    public void onServiceReady() {

        CheckUser();
    }

    private static void SetAccount(ResultSet user) throws SQLException {

        //        LinphoneManager.getCore().clearCallLogs();
        Core core = LinphoneManager.getCore();
        if (mAuthInfo == null) {
            mAuthInfo = Factory.instance().createAuthInfo(null, null, null, null, null, null);
        }

        if (mProxyConfig == null) {
            // Ensure the default configuration is loaded first
            String defaultConfig = LinphonePreferences.instance().getDefaultDynamicConfigFile();
            core.loadConfigFromXml(defaultConfig);
            mProxyConfig = core.createProxyConfig();
        }

        boolean enableDebug = false;
        // region Account Settings
        String[] enabledCodecs =
                new String[] {"opus", "g729", "pcmu", "pcma"}; // {"pcmu", "pcma"}; //
        int sipPort = user.getInt("sip_port");
        boolean useProxy = false;
        String OutboundProxy = "";
        String Expire = "30";
        String setAvpfRrInterval = "0";
        String STUNServer = "";
        String DailPrefix = "";
        boolean enableIce = false;
        AVPFMode mode = AVPFMode.Disabled;
        boolean removeplus = false;
        boolean enablePush = false;
        int TransType = TransportType.Udp.toInt();
        Address proxy =
                Factory.instance()
                        .createAddress(user.getString("pbx") + ":" + String.valueOf(sipPort));

        mAuthInfo.setUsername(user.getString("sipuser"));

        mAuthInfo.setUserid(user.getString("sipuser"));

        mAuthInfo.setHa1(null);
        mAuthInfo.setPassword(user.getString("sippass"));

        // Reset algorithm to generate correct hash depending on
        // algorithm set in next to come 401
        mAuthInfo.setAlgorithm(null);
        mAuthInfo.setDomain(user.getString("pbx"));

        mProxyConfig.edit();

        if (proxy != null) {
            mProxyConfig.setServerAddr(proxy.asString());
            if (useProxy) {
                mProxyConfig.setRoute(proxy.asString());
            }
        }

        NatPolicy natPolicy = mProxyConfig.getNatPolicy();
        Address identity = mProxyConfig.getIdentityAddress();

        if (identity == null) {
            if (core != null) {
                identity =
                        Factory.instance()
                                .createAddress(
                                        "sip:"
                                                + user.getString("sipuser")
                                                + "@"
                                                + user.getString("pbx"));
            }
        }

        if (natPolicy == null) {
            if (core != null) {
                natPolicy = core.createNatPolicy();
                mProxyConfig.setNatPolicy(natPolicy);
            }
        }
        if (natPolicy != null) {
            natPolicy.setStunServer(STUNServer);
        }

        if (natPolicy == null) {
            natPolicy = core.createNatPolicy();
            mProxyConfig.setNatPolicy(natPolicy);
        }

        if (natPolicy != null) {
            natPolicy.enableIce(enableIce);
        }

        if (identity != null) {
            identity.setUsername(user.getString("sipuser"));
            identity.setDomain(user.getString("pbx"));
            identity.setDisplayName(user.getString("sipuser"));
        }
        try {
            mProxyConfig.setExpires(Integer.parseInt(Expire));
        } catch (NumberFormatException nfe) {
            org.linphone.core.tools.Log.e(nfe);
        }
        try {
            mProxyConfig.setAvpfRrInterval(Integer.parseInt(setAvpfRrInterval));
        } catch (NumberFormatException nfe) {
            org.linphone.core.tools.Log.e(nfe);
        }
        if (useProxy) {
            mProxyConfig.setRoute(OutboundProxy);
        } else {
            mProxyConfig.setRoute(null);
        }
        mProxyConfig.setDialPrefix(DailPrefix);
        mProxyConfig.setIdentityAddress(identity);
        mProxyConfig.setAvpfMode(mode);
        mProxyConfig.setDialEscapePlus(removeplus);
        mProxyConfig.setPushNotificationAllowed(enablePush);

        String server = "sip:" + user.getString("pbx") + ":" + sipPort;
        Address serverAddr = Factory.instance().createAddress(server);
        if (serverAddr != null) {
            try {
                // serverAddr.setTransport(TransportType.fromInt(TransType));
                server = serverAddr.asString();
                mProxyConfig.setServerAddr(server);
                if (useProxy) {
                    mProxyConfig.setRoute(server);
                }
            } catch (NumberFormatException nfe) {
                org.linphone.core.tools.Log.e(nfe);
            }
        }
        mProxyConfig.enableRegister(true); // Enable

        mProxyConfig.done();

        // endregion Account Settings
        // region Advanced Settings
        mPrefs.setDebugEnabled(enableDebug);
        mPrefs.setJavaLogger(true);
        //                                mPrefs.setLogCollectionUploadServerUrl(newValue);
        //                                mPrefs.setServiceNotificationVisibility(newValue);
        mPrefs.setAutoStart(true);

        mPrefs.enableDarkMode(true);
        // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        //                                mPrefs.setRemoteProvisioningUrl(newValue);
        //                                mPrefs.setDefaultDisplayName(newValue);
        //                                mPrefs.setDefaultUsername(newValue);
        //                                mPrefs.setDeviceName(newValue);
        // endregion Advanced Settings

        // region Network Settings
        mPrefs.setWifiOnlyEnabled(false);
        mPrefs.useIpv6(true);
        mPrefs.setPushNotificationEnabled(true);
        mPrefs.useRandomPort(true);

        mPrefs.setIceEnabled(false);
        mPrefs.setTurnEnabled(false);
        mPrefs.setSipPort(sipPort);
        //                                mPrefs.setStunServer(newValue);
        //                                mPrefs.setTurnUsername(newValue);
        //                                mPrefs.setTurnPassword(newValue);
        mPrefs.powerSaverDialogPrompted(false);
        mPrefs.setServiceNotificationVisibility(true);
        LinphoneContext.instance().getNotificationManager().startForeground();
        // endregion Network Settings

        // region Audio Settings
        if (core != null) {
            for (final PayloadType pt : core.getAudioPayloadTypes()) {
                pt.enable(false);
                for (String c : enabledCodecs) {
                    if (c.toLowerCase().equals(pt.getMimeType().toLowerCase())) {
                        pt.enable(true);
                    }
                }

                /* Special case */
                //                if (pt.getMimeType().equals("mpeg4-generic")) {
                //                    codec.setTitle("AAC-ELD");
                //                }

                // pt.enable(newValue);
            }
        }
        // endregion Audio Settings

        // region Apply Settings
        if (core != null) {
            core.clearAllAuthInfo();
            core.clearProxyConfig();
            /*for (AuthInfo i : core.getAuthInfoList()) {
                core.removeAuthInfo(i);
            }

            for (ProxyConfig i : core.getProxyConfigList()) {
                core.removeProxyConfig(i);
            }*/

            core.addAuthInfo(mAuthInfo);
            core.addProxyConfig(mProxyConfig);
            core.setDefaultProxyConfig(mProxyConfig);
            core.refreshRegisters();
            //            core.refreshRegisters();
        }
        // endregion Apply Settings

        /*  core.getConfig().setBool("app", "debug", enableDebug);
        LinphoneUtils.configureLoggingService(
                enableDebug,
                LinphoneContext.instance().getApplicationContext().getString(R.string.app_name));*/

        // Set Ringtone
        //        core.getConfig().setBool("app", "device_ringtone", true);
        //        Uri defaultRingtoneUri =
        //                RingtoneManager.getActualDefaultRingtoneUri(
        //                        LinphoneContext.instance().getApplicationContext(),
        //                        RingtoneManager.TYPE_RINGTONE);
        //        //        Ringtone defaultRingtone = RingtoneManager.getRingtone(getActivity(),
        //        // defaultRingtoneUri);
        //        core.setRing(defaultRingtoneUri.getPath());
    }

    public static void appendLog(String text) {
        File logFile = new File("sdcard/xlog.file");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            // BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
