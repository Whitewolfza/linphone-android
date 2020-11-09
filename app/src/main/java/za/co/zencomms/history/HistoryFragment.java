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
package za.co.zencomms.history;

import android.Manifest;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.linphone.R;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ErrorInfo;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import za.co.zencomms.LinphoneContext;
import za.co.zencomms.LinphoneManager;
import za.co.zencomms.activities.MainActivity;
import za.co.zencomms.call.views.LinphoneLinearLayoutManager;
import za.co.zencomms.contacts.ContactsManager;
import za.co.zencomms.contacts.ContactsUpdatedListener;
import za.co.zencomms.utils.SelectableHelper;

public class HistoryFragment extends Fragment
        implements OnClickListener,
                OnItemClickListener,
                HistoryViewHolder.ClickListener,
                ContactsUpdatedListener,
                SelectableHelper.DeleteListener,
                LinphoneContext.CoreStartedListener {
    private RecyclerView mHistoryList;
    private TextView mNoCallHistory, mNoMissedCallHistory;
    private ImageView mMissedCalls, mAllCalls;
    private View mAllCallsSelected, mMissedCallsSelected;
    private boolean mOnlyDisplayMissedCalls;
    private List<CallLog> mLogs;
    private HistoryAdapter mHistoryAdapter;
    private SelectableHelper mSelectionHelper;
    private CoreListenerStub mListener;
    View view;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.history, container, false);
        mSelectionHelper = new SelectableHelper(view, this);

        mNoCallHistory = view.findViewById(R.id.no_call_history);
        mNoMissedCallHistory = view.findViewById(R.id.no_missed_call_history);

        mHistoryList = view.findViewById(R.id.history_list);

        LinearLayoutManager layoutManager = new LinphoneLinearLayoutManager(getActivity());
        mHistoryList.setLayoutManager(layoutManager);
        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mHistoryList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.divider));
        mHistoryList.addItemDecoration(dividerItemDecoration);

        mAllCalls = view.findViewById(R.id.all_calls);
        mAllCalls.setOnClickListener(this);

        mAllCallsSelected = view.findViewById(R.id.all_calls_select);

        mMissedCalls = view.findViewById(R.id.missed_calls);
        mMissedCalls.setOnClickListener(this);

        mMissedCallsSelected = view.findViewById(R.id.missed_calls_select);

        mAllCalls.setEnabled(false);
        mOnlyDisplayMissedCalls = false;

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Error) {
                            reloadData();
                        }
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        ContactsManager.getInstance().addContactsListener(this);
        LinphoneContext.instance().addCoreStartedListener(this);
        LinphoneManager.getCore().addListener(mListener);

        reloadData();
    }

    @Override
    public void onPause() {
        ContactsManager.getInstance().removeContactsListener(this);
        LinphoneContext.instance().removeCoreStartedListener(this);
        LinphoneManager.getCore().removeListener(mListener);

        super.onPause();
    }

    @Override
    public void onContactsUpdated() {
        HistoryAdapter adapter = (HistoryAdapter) mHistoryList.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCoreStarted() {
        reloadData();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.all_calls) {
            mAllCalls.setEnabled(false);
            mAllCallsSelected.setVisibility(View.VISIBLE);
            mMissedCallsSelected.setVisibility(View.INVISIBLE);
            mMissedCalls.setEnabled(true);
            mOnlyDisplayMissedCalls = false;
            refresh();
        }
        if (id == R.id.missed_calls) {
            mAllCalls.setEnabled(true);
            mAllCallsSelected.setVisibility(View.INVISIBLE);
            mMissedCallsSelected.setVisibility(View.VISIBLE);
            mMissedCalls.setEnabled(false);
            mOnlyDisplayMissedCalls = true;
        }
        hideHistoryListAndDisplayMessageIfEmpty();
        mHistoryAdapter =
                new HistoryAdapter((HistoryActivity) getActivity(), mLogs, this, mSelectionHelper);
        mHistoryList.setAdapter(mHistoryAdapter);
        mSelectionHelper.setAdapter(mHistoryAdapter);
        mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        if (mHistoryAdapter.isEditionEnabled()) {
            CallLog log = mLogs.get(position);
            Core core = LinphoneManager.getCore();
            core.removeCallLog(log);
            mLogs = Arrays.asList(core.getCallLogs());
            mLogs = new ArrayList<>(mLogs);
            mLogs.addAll(getLocalCallLogs());
        }
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = mHistoryAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            CallLog log = (CallLog) objectsToDelete[i];
            LinphoneManager.getCore().removeCallLog(log);
            onResume();
        }
    }

    @Override
    public void onItemClicked(int position) {
        if (mHistoryAdapter.isEditionEnabled()) {
            mHistoryAdapter.toggleSelection(position);
        } else {
            if (position >= 0 && position < mLogs.size()) {
                CallLog log = mLogs.get(position);
                Address address;
                if (log.getDir() == Call.Dir.Incoming) {
                    address = log.getFromAddress();
                } else {
                    address = log.getToAddress();
                }
                if (address != null) {
                    ((MainActivity) getActivity()).newOutgoingCall(address.asStringUriOnly());
                }
            }
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mHistoryAdapter.isEditionEnabled()) {
            mSelectionHelper.enterEditionMode();
        }
        mHistoryAdapter.toggleSelection(position);
        return true;
    }

    private void refresh() {
        mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
        mLogs = new ArrayList<>(mLogs);
        mLogs.addAll(getLocalCallLogs());
    }

    public void displayFirstLog() {
        Address addr;
        if (mLogs != null && mLogs.size() > 0) {
            CallLog log = mLogs.get(0); // More recent one is 0
            if (log.getDir() == Call.Dir.Incoming) {
                addr = log.getFromAddress();
            } else {
                addr = log.getToAddress();
            }
            ((HistoryActivity) getActivity()).showHistoryDetails(addr);
        } else {
            ((HistoryActivity) getActivity()).showEmptyChildFragment();
        }
    }

    private void reloadData() {
        mLogs = Arrays.asList(LinphoneManager.getCore().getCallLogs());
        mLogs = new ArrayList<>(mLogs);
        mLogs.addAll(getLocalCallLogs());
        hideHistoryListAndDisplayMessageIfEmpty();
        mHistoryAdapter =
                new HistoryAdapter((HistoryActivity) getActivity(), mLogs, this, mSelectionHelper);
        mHistoryList.setAdapter(mHistoryAdapter);
        mSelectionHelper.setAdapter(mHistoryAdapter);
        mSelectionHelper.setDialogMessage(R.string.call_log_delete_dialog);
    }

    private void removeNotMissedCallsFromLogs() {
        if (mOnlyDisplayMissedCalls) {
            List<CallLog> missedCalls = new ArrayList<>();
            for (CallLog log : mLogs) {
                if (log.getStatus() == Call.Status.Missed) {
                    missedCalls.add(log);
                }
            }
            mLogs = missedCalls;
        }
    }

    private void hideHistoryListAndDisplayMessageIfEmpty() {
        removeNotMissedCallsFromLogs();
        mNoCallHistory.setVisibility(View.GONE);
        mNoMissedCallHistory.setVisibility(View.GONE);

        if (mLogs.isEmpty()) {
            if (mOnlyDisplayMissedCalls) {
                mNoMissedCallHistory.setVisibility(View.VISIBLE);
            } else {
                mNoCallHistory.setVisibility(View.VISIBLE);
            }
            mHistoryList.setVisibility(View.GONE);
        } else {
            mNoCallHistory.setVisibility(View.GONE);
            mNoMissedCallHistory.setVisibility(View.GONE);
            mHistoryList.setVisibility(View.VISIBLE);
        }
    }

    private List<CallLog> getLocalCallLogs() {

        List<CallLog> locallogs = new ArrayList<>();

        if (view.getContext().checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED) {
            ContentResolver cr = view.getContext().getContentResolver();
            final Cursor c =
                    cr.query(
                            android.provider.CallLog.Calls.CONTENT_URI,
                            null,
                            null,
                            null,
                            android.provider.CallLog.Calls.DATE + " DESC");
            int number = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER);
            int type = c.getColumnIndex(android.provider.CallLog.Calls.TYPE);
            final int date = c.getColumnIndex(android.provider.CallLog.Calls.DATE);
            int duration = c.getColumnIndex(android.provider.CallLog.Calls.DURATION);
            while (c.moveToNext()) {
                final String phNumber = c.getString(number);
                String callType = c.getString(type);
                final long callDate = c.getLong(date);
                Date callDayTime = new Date(callDate);

                final String callDuration = c.getString(duration);
                Call.Dir dir = null;
                String status = null;
                int dircode = Integer.parseInt(callType);
                switch (dircode) {
                    case android.provider.CallLog.Calls.OUTGOING_TYPE:
                        dir = Call.Dir.Outgoing;
                        status = "In Call";
                        //                    dir = "OUTGOING";
                        break;
                    case android.provider.CallLog.Calls.INCOMING_TYPE:
                        dir = Call.Dir.Incoming;
                        status = "In Call";
                        //                    dir = "INCOMING";
                        break;
                    case android.provider.CallLog.Calls.MISSED_TYPE:
                        dir = Call.Dir.Incoming;
                        status = "";
                        //                    dir = "MISSED";
                        break;
                }

                final Call.Dir finalDir = dir;
                CallLog entry =
                        new CallLog() {
                            @NonNull
                            @Override
                            public String getCallId() {
                                return phNumber;
                            }

                            @Override
                            public Call.Dir getDir() {
                                return finalDir;
                            }

                            @Override
                            public int getDuration() {
                                return Integer.parseInt(callDuration);
                            }

                            @Nullable
                            @Override
                            public ErrorInfo getErrorInfo() {
                                return null;
                            }

                            @NonNull
                            @Override
                            public Address getFromAddress() {
                                // return null;
                                if (finalDir == Call.Dir.Incoming) {
                                    Core core = LinphoneManager.getCore();
                                    ProxyConfig config = core.getDefaultProxyConfig();
                                    return Factory.instance()
                                            .createAddress(
                                                    "sip:"
                                                            + phNumber
                                                            + "@"
                                                            + config.getServerAddr()
                                                                    .replace("sip:", ""));
                                } else {
                                    return null;
                                }
                            }

                            @NonNull
                            @Override
                            public Address getLocalAddress() {
                                return null;
                            }

                            @Override
                            public float getQuality() {
                                return 0;
                            }

                            @Nullable
                            @Override
                            public String getRefKey() {
                                return null;
                            }

                            @Override
                            public void setRefKey(@Nullable String refkey) {}

                            @NonNull
                            @Override
                            public Address getRemoteAddress() {
                                return null;
                            }

                            @Override
                            public long getStartDate() {
                                return (callDate / 1000);
                            }

                            @Override
                            public Call.Status getStatus() {
                                return null;
                            }

                            @NonNull
                            @Override
                            public Address getToAddress() {
                                if (finalDir == Call.Dir.Outgoing) {
                                    Core core = LinphoneManager.getCore();
                                    ProxyConfig config = core.getDefaultProxyConfig();
                                    return Factory.instance()
                                            .createAddress(
                                                    "sip:"
                                                            + phNumber
                                                            + "@"
                                                            + config.getServerAddr()
                                                                    .replace("sip:", ""));
                                } else {
                                    return null;
                                }
                            }

                            @Override
                            public boolean videoEnabled() {
                                return false;
                            }

                            @NonNull
                            @Override
                            public String toStr() {
                                return null;
                            }

                            @Override
                            public boolean wasConference() {
                                return false;
                            }

                            @Override
                            public void setUserData(Object data) {}

                            @Override
                            public Object getUserData() {
                                return null;
                            }

                            @Override
                            public long getNativePointer() {
                                return 0;
                            }

                            @Override
                            public String toString() {
                                return null;
                            }
                        };

                //            entry.log_id = -1;
                //            entry.callstart = Functions.FormatDate(callDayTime, "yyyy-MM-dd
                // HH:mm:ss.SSS");
                //            entry.callend = Functions.FormatDate(callDayTime, "yyyy-MM-dd
                // HH:mm:ss.SSS");
                //            entry.callingnumbner = phNumber;
                //            assert dir != null;
                //            entry.direction= dir;
                //            entry.duration = callDuration;
                //            entry.result = status;
                //            entry.uid = "00";
                locallogs.add(entry);
            }
            c.close();
        }

        return locallogs;
    }
}
