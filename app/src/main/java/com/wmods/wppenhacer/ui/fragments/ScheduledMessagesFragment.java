package com.wmods.wppenhacer.ui.fragments;

import androidx.activity.OnBackPressedCallback;
import com.wmods.wppenhacer.models.ScheduledMessage;
import androidx.activity.result.contract.ActivityResultContracts;
import com.wmods.wppenhacer.db.ScheduledMessageDatabase;
import androidx.activity.result.ActivityResultLauncher;

import com.wmods.wppenhacer.ui.adapters.ScheduledMessagesAdapter;
import androidx.core.content.ContextCompat;
import com.wmods.wppenhacer.databinding.FragmentScheduledMessagesBinding;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.appcompat.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.services.ScheduledMessageService;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessagesFragment extends Fragment implements ScheduledMessagesAdapter.b {
    public FragmentScheduledMessagesBinding B;
    public ScheduledMessagesAdapter C;
    public ScheduledMessageDatabase D;

    /* JADX INFO: renamed from: X, reason: collision with root package name */
    public ActivityResultLauncher<String> f127X;
    public String q;
    public String s;
    public static final String Z = "ScheduledMessagesFragment";
    public static final String F0 = "arg_contact_jid";
    public static final String G0 = "arg_contact_name";
    public static final String H0 = "arg_whatsapp_type";
    public static final String I0 = "schedule_editor";
    public int x = -1;
    public boolean y = false;
    public boolean z = false;
    public final Handler A = new Handler(Looper.getMainLooper());
    public final BroadcastReceiver Y = new a();

    /* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
    public class a extends BroadcastReceiver {
        public a() {
        }

        public final /* synthetic */ void b(boolean z) {
            if (!ScheduledMessagesFragment.this.isAdded() || ScheduledMessagesFragment.this.B == null) {
                return;
            }
            ScheduledMessagesFragment.this.H();
            if (z) {
                Toast.makeText(ScheduledMessagesFragment.this.getContext(), R.string.schedule_sent, 0).show();
            }
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if ("com.wmods.wppenhacer.MESSAGE_SENT".equals(intent.getAction())) {
                long longExtra = intent.getLongExtra("message_id", -1L);
                final boolean booleanExtra = intent.getBooleanExtra("success", true);
                Log.d("ScheduledMessagesFragment", "Received message result broadcast for ID: " + longExtra + ", success=" + booleanExtra);
                ScheduledMessagesFragment.this.A.post(() -> b(booleanExtra));
            }
        }
    }

    /* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
    public class b extends OnBackPressedCallback {
        public b(boolean z) {
            super(z);
        }

        @Override // X.dx1
        public void handleOnBackPressed() {
            if (ScheduledMessagesFragment.this.getParentFragmentManager().getBackStackEntryCount() > 0) {
                ScheduledMessagesFragment.this.getParentFragmentManager().popBackStack();
            } else {
                setEnabled(false);
                ScheduledMessagesFragment.this.requireActivity().onBackPressed();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void A(Boolean bool) {
        if (!bool.booleanValue()) {
            this.y = false;
            N();
            return;
        }
        Toast.makeText(getContext(), R.string.enable_notifications, 0).show();
        if (ScheduledMessageService.m(requireContext())) {
            ScheduledMessageService.n(requireContext());
        }
        if (this.y) {
            this.y = false;
            J(null, true);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void C(View view) {
        y();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void D(View view) {
        y();
    }

    public static ScheduledMessagesFragment I(String str, String str2, int i) {
        ScheduledMessagesFragment scheduledMessagesFragment = new ScheduledMessagesFragment();
        Bundle bundle = new Bundle();
        bundle.putString("arg_contact_jid", str);
        bundle.putString("arg_contact_name", str2);
        bundle.putInt("arg_whatsapp_type", i);
        scheduledMessagesFragment.setArguments(bundle);
        return scheduledMessagesFragment;
    }

    private void M(boolean z) {
        ActionBar supportActionBar;
        if (getActivity() == null || (supportActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar()) == null) {
            return;
        }
        supportActionBar.setDisplayHomeAsUpEnabled(z);
    }

    public final /* synthetic */ void B(ScheduledMessage ScheduledMessageVar, DialogInterface dialogInterface, int i) {
        this.D.m(ScheduledMessageVar.k());
        H();
        Toast.makeText(getContext(), R.string.schedule_deleted, 0).show();
    }

    public final /* synthetic */ void E() {
        if (z()) {
            J(null, true);
        } else {
            this.y = true;
            L();
        }
    }

    public final /* synthetic */ void F(DialogInterface dialogInterface, int i) {
        Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }

    public final /* synthetic */ void G(DialogInterface dialogInterface, int i) {
        L();
    }

    public final void H() {
        List<ScheduledMessage> listP = this.D.p();
        this.C.g(listP);
        Q(listP);
        boolean zIsEmpty = listP.isEmpty();
        this.B.emptyState.setVisibility(zIsEmpty ? 0 : 8);
        this.B.recyclerScheduled.setVisibility(zIsEmpty ? 8 : 0);
    }

    public final void J(ScheduledMessage ScheduledMessageVar, boolean z) {
        ScheduleEditorFragment scheduleEditorFragmentQ = ScheduleEditorFragment.Q(ScheduledMessageVar != null ? ScheduledMessageVar.k() : -1L, z ? this.q : null, z ? this.s : null, z ? this.x : -1);
        int id = getId();
        if (id == -1 && getView() != null && (getView().getParent() instanceof View)) {
            id = ((View) getView().getParent()).getId();
        }
        if (id == -1) {
            Log.w("ScheduledMessagesFragment", "Failed to resolve fragment container id for editor");
        } else {
            getParentFragmentManager().beginTransaction().replace(id, scheduleEditorFragmentQ).addToBackStack("schedule_editor").commit();
        }
    }

    public final void K() {
        try {
            IntentFilter intentFilter = new IntentFilter("com.wmods.wppenhacer.MESSAGE_SENT");
            if (Build.VERSION.SDK_INT >= 33) {
                requireContext().registerReceiver(this.Y, intentFilter, 4);
            } else {
                requireContext().registerReceiver(this.Y, intentFilter);
            }
            Log.d("ScheduledMessagesFragment", "Message sent receiver registered");
        } catch (Exception e) {
            Log.e("ScheduledMessagesFragment", "Failed to register receiver", e);
        }
    }

    public final void L() {
        if (Build.VERSION.SDK_INT >= 33) {
            this.f127X.launch("android.permission.POST_NOTIFICATIONS");
        }
    }

    public final void N() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.notification_permission_required).setMessage(R.string.notification_permission_message).setPositiveButton(R.string.enable_notifications, (dialogInterface, i) -> F(dialogInterface, i)).setNegativeButton(R.string.cancel, null).show();
    }

    public final void O() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.notification_permission_required).setMessage(R.string.notification_permission_message).setPositiveButton(R.string.enable_notifications, (dialogInterface, i) -> G(dialogInterface, i)).setNegativeButton(R.string.cancel, null).show();
    }

    public final void P() {
        try {
            requireContext().unregisterReceiver(this.Y);
            Log.d("ScheduledMessagesFragment", "Message sent receiver unregistered");
        } catch (Exception e) {
            Log.e("ScheduledMessagesFragment", "Failed to unregister receiver", e);
        }
    }

    public final void Q(List<ScheduledMessage> list) {
        List<ScheduledMessage> listO = this.D.o();
        int size = listO.size();
        ScheduledMessage ScheduledMessageVarW = w(listO);
        String string = size > 0 ? getString(R.string.scheduled_messages_active, Integer.valueOf(size)) : getString(R.string.scheduled_messages);
        String string2 = size == 0 ? list.isEmpty() ? getString(R.string.scheduled_messages_monitoring) : getString(R.string.scheduled_messages_active, 0) : ScheduledMessageVarW != null ? getString(R.string.scheduled_messages_next, Integer.valueOf(size), ScheduledMessageVarW.g(), x(ScheduledMessageVarW.p())) : getString(R.string.scheduled_messages_active, Integer.valueOf(size));
        this.B.textSummaryTitle.setText(string);
        this.B.textSummaryDetail.setText(string2);
    }

    @Override // com.wmods.wppenhacer.ui.adapters.ScheduledMessagesAdapter.b
    public void b(ScheduledMessage ScheduledMessageVar, boolean z) {
        if (z && ScheduledMessageVar.r() == 0 && ScheduledMessageVar.s() <= System.currentTimeMillis()) {
            Toast.makeText(getContext(), R.string.schedule_error_time, 0).show();
            this.C.notifyDataSetChanged();
            return;
        }
        this.D.R(ScheduledMessageVar.k(), z);
        ScheduledMessageVar.A(z);
        this.C.notifyDataSetChanged();
        Q(this.D.p());
        if (z && ScheduledMessageService.m(getContext())) {
            ScheduledMessageService.n(getContext());
        }
    }

    @Override // com.wmods.wppenhacer.ui.adapters.ScheduledMessagesAdapter.b
    public void c(ScheduledMessage ScheduledMessageVar) {
        J(ScheduledMessageVar, false);
    }

    @Override // com.wmods.wppenhacer.ui.adapters.ScheduledMessagesAdapter.b
    public void d(final ScheduledMessage ScheduledMessageVar) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.swipe_conversation_action_delete).setPositiveButton(R.string.yes, (dialogInterface, i) -> B(ScheduledMessageVar, dialogInterface, i)).setNegativeButton(R.string.no, null).show();
    }

    @Override // androidx.fragment.app.e
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (getArguments() != null) {
            this.q = getArguments().getString("arg_contact_jid");
            this.s = getArguments().getString("arg_contact_name");
            this.x = getArguments().getInt("arg_whatsapp_type", -1);
            String str = this.q;
            this.z = (str == null || str.isEmpty()) ? false : true;
        }
        this.f127X = registerForActivityResult(new ActivityResultContracts.RequestPermission(), obj -> A((Boolean) obj));
    }

    @Override // androidx.fragment.app.e
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        FragmentScheduledMessagesBinding rw0VarC = FragmentScheduledMessagesBinding.inflate(layoutInflater, viewGroup, false);
        this.B = rw0VarC;
        return rw0VarC.getRoot();
    }

    @Override // androidx.fragment.app.e
    public void onDestroyView() {
        super.onDestroyView();
        P();
        this.B = null;
    }

    @Override // androidx.fragment.app.e
    public void onResume() {
        super.onResume();
        M(true);
        H();
    }

    @Override // androidx.fragment.app.e
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new b(true));
        this.D = ScheduledMessageDatabase.v(getContext());
        this.C = new ScheduledMessagesAdapter(this);
        this.B.recyclerScheduled.setLayoutManager(new LinearLayoutManager(getContext()));
        this.B.recyclerScheduled.setHasFixedSize(true);
        this.B.recyclerScheduled.setAdapter(this.C);
        this.B.fabAdd.setOnClickListener(view2 -> y());
        this.B.buttonEmptyAdd.setOnClickListener(view2 -> y());
        H();
        v();
        K();
        if (this.z) {
            this.z = false;
            this.A.post(() -> E());
        }
    }

    public final void v() {
        if (z() || this.D.o().isEmpty()) {
            return;
        }
        O();
    }

    public final ScheduledMessage w(List<ScheduledMessage> list) {
        long j = Long.MAX_VALUE;
        ScheduledMessage ScheduledMessageVar = null;
        for (ScheduledMessage ScheduledMessageVar2 : list) {
            long jP = ScheduledMessageVar2.p();
            if (jP < j && !ScheduledMessageVar2.J()) {
                ScheduledMessageVar = ScheduledMessageVar2;
                j = jP;
            }
        }
        return ScheduledMessageVar;
    }

    public final String x(long j) {
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Long.valueOf(j));
    }

    public final void y() {
        if (z()) {
            J(null, false);
        } else {
            this.y = true;
            L();
        }
    }

    public final boolean z() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(requireContext(), "android.permission.POST_NOTIFICATIONS") == 0;
    }
}
