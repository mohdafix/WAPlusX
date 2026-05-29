package com.wmods.wppenhacer.ui.fragments;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import com.wmods.wppenhacer.xposed.utils.Utils;
import com.wmods.wppenhacer.models.ScheduledMessage;
import com.wmods.wppenhacer.db.ScheduledMessageDatabase;
import androidx.activity.result.ActivityResultLauncher;

import com.wmods.wppenhacer.models.ScheduledContact;
import com.wmods.wppenhacer.databinding.FragmentScheduleEditorBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import com.wmods.wppenhacer.models.Contact;
import androidx.activity.result.ActivityResultCallback;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.services.ScheduledMessageService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduleEditorFragment extends Fragment {
    public static final String F0 = "ScheduleEditorFragment";
    public static final String G0 = "arg_message_id";
    public static final String H0 = "arg_contact_jid";
    public static final String I0 = "arg_contact_name";
    public static final String J0 = "arg_whatsapp_type";
    public String A;
    public String D;

    /* JADX INFO: renamed from: X, reason: collision with root package name */
    public String f126X;
    public ActivityResultLauncher<String> Z;
    public FragmentScheduleEditorBinding q;
    public ScheduledMessageDatabase s;
    public ScheduledMessage x;
    public Calendar z;
    public final ArrayList<ScheduledContact> y = new ArrayList<>();
    public int B = 0;
    public int C = 0;
    public int Y = -1;



    public static ScheduleEditorFragment Q(long j, String str, String str2, int i) {
        ScheduleEditorFragment scheduleEditorFragment = new ScheduleEditorFragment();
        Bundle bundle = new Bundle();
        bundle.putLong("arg_message_id", j);
        bundle.putString("arg_contact_jid", str);
        bundle.putString("arg_contact_name", str2);
        bundle.putInt("arg_whatsapp_type", i);
        scheduleEditorFragment.setArguments(bundle);
        return scheduleEditorFragment;
    }

    private void R() {
        try {
            ArrayList<String> selectedJids = new ArrayList<>();
            for (ScheduledContact sc : this.y) {
                selectedJids.add(sc.b());
            }
            String targetPkg = this.C == 1 ? "com.whatsapp.w4b" : "com.whatsapp";
            Intent intent = com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher.createPickerIntent(requireContext(), targetPkg, "schedule_message_contacts", selectedJids);
            startActivityForResult(intent, 16721174);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to open picker: " + e.getMessage(), 0).show();
        }
    }

    private void Z() {
        if (this.q == null) {
            return;
        }
        if (this.y.isEmpty()) {
            this.q.editContact.setText("");
            return;
        }
        if (this.y.size() == 1) {
            this.q.editContact.setText(this.y.get(0).a());
            this.q.inputContact.setError(null);
            return;
        }
        this.q.editContact.setText(this.y.size() + " " + getString(R.string.schedule_contacts_selected));
        this.q.inputContact.setError(null);
    }

    public final void A() {
        if (this.A != null) {
            File file = new File(this.A);
            if (file.exists() && file.getParent() != null && file.getParent().contains("scheduled_images")) {
                file.delete();
            }
        }
        this.A = null;
        b0();
    }

    public final void B() {
        FragmentScheduleEditorBinding qw0Var = this.q;
        if (qw0Var == null) {
            return;
        }
        qw0Var.inputContact.setError(null);
        this.q.inputMessage.setError(null);
        this.q.inputDate.setError(null);
        this.q.inputTime.setError(null);
    }

    public final String C(Uri uri) {
        try {
            Context contextRequireContext = requireContext();
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WaEnhancer/.scheduled_images");
            if (!file.exists()) {
                file.mkdirs();
            }
            File file2 = new File(file, "scheduled_img_" + System.currentTimeMillis() + ".jpg");
            InputStream inputStreamOpenInputStream = contextRequireContext.getContentResolver().openInputStream(uri);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file2);
                if (inputStreamOpenInputStream == null) {
                    fileOutputStream.close();
                    if (inputStreamOpenInputStream != null) {
                        inputStreamOpenInputStream.close();
                    }
                    return null;
                }
                try {
                    byte[] bArr = new byte[4096];
                    while (true) {
                        int i = inputStreamOpenInputStream.read(bArr);
                        if (i == -1) {
                            fileOutputStream.close();
                            inputStreamOpenInputStream.close();
                            file2.setReadable(true, false);
                            Log.d("ScheduleEditorFragment", "Image copied to: " + file2.getAbsolutePath());
                            return file2.getAbsolutePath();
                        }
                        fileOutputStream.write(bArr, 0, i);
                    }
                } finally {
                }
            } finally {
            }
        } catch (Exception e) {
            Log.e("ScheduleEditorFragment", "Failed to copy image", e);
            return null;
        }
    }

    public final /* synthetic */ void D(Uri uri) {
        if (uri != null) {
            String strC = C(uri);
            if (strC == null) {
                Toast.makeText(getContext(), R.string.error_when_saving_try_again, 0).show();
            } else {
                this.A = strC;
                b0();
            }
        }
    }





    public final /* synthetic */ void K(TimePicker timePicker, int i, int i2) {
        this.z.set(11, i);
        this.z.set(12, i2);
        FragmentScheduleEditorBinding qw0Var = this.q;
        a0(qw0Var.editDate, qw0Var.editTime);
    }

    public final /* synthetic */ void L(View view) {
        new TimePickerDialog(requireContext(), (timePicker, i, i2) -> K(timePicker, i, i2), this.z.get(11), this.z.get(12), false).show();
    }

    public final /* synthetic */ void M(DatePicker datePicker, int i, int i2, int i3) {
        this.z.set(1, i);
        this.z.set(2, i2);
        this.z.set(5, i3);
        FragmentScheduleEditorBinding qw0Var = this.q;
        a0(qw0Var.editDate, qw0Var.editTime);
    }

    public final /* synthetic */ void N(View view) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), (datePicker, i, i2, i3) -> M(datePicker, i, i2, i3), this.z.get(1), this.z.get(2), this.z.get(5));
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    public final /* synthetic */ void O(ChipGroup chipGroup, List list) {
        int checkedChipId = chipGroup.getCheckedChipId();
        if (checkedChipId == R.id.chip_repeat_daily) {
            this.B = 1;
            this.q.daysOfWeekContainer.setVisibility(8);
            return;
        }
        if (checkedChipId == R.id.chip_repeat_weekly) {
            this.B = 2;
            this.q.daysOfWeekContainer.setVisibility(8);
        } else if (checkedChipId == R.id.chip_repeat_monthly) {
            this.B = 3;
            this.q.daysOfWeekContainer.setVisibility(8);
        } else if (checkedChipId == R.id.chip_repeat_custom) {
            this.B = 4;
            this.q.daysOfWeekContainer.setVisibility(0);
        } else {
            this.B = 0;
            this.q.daysOfWeekContainer.setVisibility(8);
        }
    }

    public final /* synthetic */ void P(MaterialButtonToggleGroup materialButtonToggleGroup, int i, boolean z) {
        if (z) {
            if (i == R.id.toggle_whatsapp_business) {
                this.C = 1;
            } else {
                this.C = 0;
            }
        }
    }

    public final void S() {
        if (this.x == null) {
            this.z.add(12, 5);
            String str = this.D;
            if (str != null && !str.isEmpty()) {
                String str2 = this.f126X;
                String str3 = (str2 == null || str2.isEmpty()) ? this.D.split("@")[0] : this.f126X;
                this.y.clear();
                this.y.add(new ScheduledContact(str3, this.D));
                Z();
            }
            if (this.Y >= 0 && this.q.cardWhatsappType.getVisibility() == 0) {
                this.C = this.Y;
            }
            c0();
            return;
        }
        this.y.clear();
        this.z.setTimeInMillis(this.x.s());
        List<String> listD = this.x.d();
        List<String> listF = this.x.f();
        int i = 0;
        while (i < listD.size()) {
            String str4 = listD.get(i);
            this.y.add(new ScheduledContact(i < listF.size() ? listF.get(i) : str4.split("@")[0], str4));
            i++;
        }
        Z();
        this.q.editMessage.setText(this.x.n());
        this.A = this.x.l();
        if (this.q.cardWhatsappType.getVisibility() == 0) {
            this.C = this.x.t();
        }
        c0();
        z(this.x.r());
        y(this.x.q());
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v11, types: [com.wmods.wppenhacer.db.ScheduledMessageDatabase] */
    /* JADX WARN: Type inference failed for: r0v26 */
    /* JADX WARN: Type inference failed for: r0v27 */
    /* JADX WARN: Type inference failed for: r0v28 */
    /* JADX WARN: Type inference failed for: r0v29 */
    /* JADX WARN: Type inference failed for: r0v30 */
    /* JADX WARN: Type inference failed for: r0v31 */
    /* JADX WARN: Type inference failed for: r0v65 */
    /* JADX WARN: Type inference failed for: r0v66 */
    /* JADX WARN: Type inference failed for: r0v67 */
    /* JADX WARN: Type inference failed for: r0v68 */
    /* JADX WARN: Type inference failed for: r0v69 */
    /* JADX WARN: Type inference failed for: r0v7 */
    /* JADX WARN: Type inference failed for: r0v70 */
    /* JADX WARN: Type inference failed for: r0v71 */
    /* JADX WARN: Type inference failed for: r0v72 */
    /* JADX WARN: Type inference failed for: r0v73 */
    /* JADX WARN: Type inference failed for: r0v74 */
    /* JADX WARN: Type inference failed for: r0v75 */
    /* JADX WARN: Type inference failed for: r0v76 */
    /* JADX WARN: Type inference failed for: r0v8, types: [int] */
    /* JADX WARN: Type inference failed for: r3v9, types: [com.wmods.wppenhacer.models.ScheduledMessage] */
    /* JADX WARN: Type inference failed for: r6v2, types: [com.wmods.wppenhacer.models.ScheduledMessage] */
    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:593)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    public final void T() {
        boolean z;
        int r0;
        B();
        if (this.y.isEmpty()) {
            this.q.inputContact.setError(getString(R.string.schedule_error_contact));
            Toast.makeText(getContext(), R.string.schedule_error_contact, 0).show();
            z = true;
        } else {
            z = false;
        }
        String strTrim = this.q.editMessage.getText() != null ? this.q.editMessage.getText().toString().trim() : "";
        boolean z2 = z;
        if (strTrim.isEmpty()) {
            this.q.inputMessage.setError(getString(R.string.schedule_error_message));
            Toast.makeText(getContext(), R.string.schedule_error_message, 0).show();
            z2 = true;
        }
        boolean z3 = z2;
        if (this.z.getTimeInMillis() <= System.currentTimeMillis()) {
            this.q.inputDate.setError(getString(R.string.schedule_error_time));
            this.q.inputTime.setError(" ");
            Toast.makeText(getContext(), R.string.schedule_error_time, 0).show();
            z3 = true;
        }
        if (z3) {
            return;
        }
        if (this.B == 4) {
            boolean zIsChecked = this.q.chipSunday.isChecked();
            int r02 = zIsChecked ? 1 : 0;
            if (this.q.chipMonday.isChecked()) {
                r02 = r02 | 2;
            }
            int r03 = r02;
            if (this.q.chipTuesday.isChecked()) {
                r03 = r02 | 4;
            }
            int r04 = r03;
            if (this.q.chipWednesday.isChecked()) {
                r04 = r03 | 8;
            }
            int r05 = r04;
            if (this.q.chipThursday.isChecked()) {
                r05 = r04 | 16;
            }
            int r06 = r05;
            if (this.q.chipFriday.isChecked()) {
                r06 = r05 | 32;
            }
            r0 = r06;
            if (this.q.chipSaturday.isChecked()) {
                r0 = r06 | 64;
            }
            if (r0 == 0) {
                Toast.makeText(getContext(), R.string.schedule_error_days, 0).show();
                return;
            }
        } else {
            r0 = 0;
        }
        this.z.set(13, 0);
        this.z.set(14, 0);
        long timeInMillis = this.z.getTimeInMillis();
        ScheduledMessage ScheduledMessageVar = this.x;
        if (ScheduledMessageVar != null) {
            ScheduledMessageVar.b();
            for (ScheduledContact ScheduledContactVar : this.y) {
                this.x.a(ScheduledContactVar.b(), ScheduledContactVar.a());
            }
            this.x.D(strTrim);
            this.x.G(timeInMillis);
            this.x.F(this.B);
            this.x.E(r0);
            this.x.I(this.C);
            this.x.C(this.A);
            this.x.A(true);
            this.x.H(false);
            this.s.V(this.x);
            Toast.makeText(getContext(), R.string.schedule_updated, 0).show();
        } else {
            ScheduledMessage ScheduledMessageVar2 = new ScheduledMessage();
            for (ScheduledContact ScheduledContactVar2 : this.y) {
                ScheduledMessageVar2.a(ScheduledContactVar2.b(), ScheduledContactVar2.a());
            }
            ScheduledMessageVar2.D(strTrim);
            ScheduledMessageVar2.G(timeInMillis);
            ScheduledMessageVar2.F(this.B);
            ScheduledMessageVar2.E(r0);
            ScheduledMessageVar2.I(this.C);
            ScheduledMessageVar2.C(this.A);
            ScheduledMessageVar2.A(true);
            this.s.F(ScheduledMessageVar2);
            Toast.makeText(getContext(), R.string.schedule_created, 0).show();
        }
        ScheduledMessageService.n(requireContext());
        getParentFragmentManager().popBackStack();
    }

    public final void U() {
        FragmentActivity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            AppCompatActivity tcVar = (AppCompatActivity) getActivity();
            if (tcVar != null && tcVar.getSupportActionBar() != null) {
                tcVar.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                tcVar.getSupportActionBar().setTitle(R.string.schedule_message_menu);
            }
        }
    }

    public final void V() {
        this.q.editDate.setOnClickListener(view -> N(view));
        this.q.editTime.setOnClickListener(view -> L(view));
    }

    public final void W() {
        this.q.groupRepeat.setOnCheckedStateChangeListener((chipGroup, list) -> O(chipGroup, list));
        this.q.chipRepeatOnce.setChecked(true);
    }

    public final void X() {
        if (this.y.size() > 1) {
            this.q.cardWhatsappType.setVisibility(0);
            this.q.toggleWhatsappType.addOnButtonCheckedListener((materialButtonToggleGroup, i, z) -> P(materialButtonToggleGroup, i, z));
        } else if (!"com.wmods.wppenhacer".endsWith(".w4b")) {
            this.q.toggleWhatsappType.addOnButtonCheckedListener((materialButtonToggleGroup, i, z) -> P(materialButtonToggleGroup, i, z));
        } else {
            this.q.cardWhatsappType.setVisibility(8);
            this.C = 1;
        }
    }

    public final void a0(TextInputEditText textInputEditText, TextInputEditText textInputEditText2) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        textInputEditText.setText(simpleDateFormat.format(this.z.getTime()));
        textInputEditText2.setText(simpleDateFormat2.format(this.z.getTime()));
        FragmentScheduleEditorBinding qw0Var = this.q;
        if (qw0Var != null) {
            qw0Var.inputContact.setError(null);
            this.q.inputTime.setError(null);
        }
    }

    public final void b0() {
        if (this.q == null) {
            return;
        }
        String str = this.A;
        if (str == null || str.isEmpty()) {
            this.q.imagePreviewContainer.setVisibility(8);
            this.q.btnAttachImage.setVisibility(0);
            this.q.btnRemoveImage.setVisibility(8);
            return;
        }
        File file = new File(this.A);
        if (!file.exists()) {
            A();
            return;
        }
        this.q.imagePreview.setImageURI(Uri.fromFile(file));
        this.q.imagePreviewContainer.setVisibility(0);
        this.q.btnAttachImage.setVisibility(8);
        this.q.btnRemoveImage.setVisibility(0);
    }

    public final void c0() {
        if (this.q.cardWhatsappType.getVisibility() != 0) {
            return;
        }
        if (this.C == 1) {
            this.q.toggleWhatsappType.check(R.id.toggle_whatsapp_business);
        } else {
            this.q.toggleWhatsappType.check(R.id.toggle_whatsapp_normal);
        }
    }

    @Override // androidx.fragment.app.e
    public void onActivityResult(int i, int i2, Intent intent) {
        if (i == 16721174 && i2 == -1 && intent != null) {
            if (intent.hasExtra("picker_contacts")) {
                ArrayList<com.wmods.wppenhacer.model.ContactPickerResult> selectedContacts = (ArrayList<com.wmods.wppenhacer.model.ContactPickerResult>) intent.getSerializableExtra("picker_contacts");
                if (selectedContacts != null) {
                    this.y.clear();
                    for (com.wmods.wppenhacer.model.ContactPickerResult data : selectedContacts) {
                        this.y.add(new ScheduledContact(data.fullName(), data.jid()));
                    }
                    Z();
                }
            }
        }
    }

    @Override // androidx.fragment.app.e
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (getArguments() != null) {
            this.D = getArguments().getString("arg_contact_jid");
            this.f126X = getArguments().getString("arg_contact_name");
            this.Y = getArguments().getInt("arg_whatsapp_type", -1);
        }
        this.Z = registerForActivityResult(new ActivityResultContracts.GetContent(), obj -> D((Uri) obj));
    }

    @Override // androidx.fragment.app.e
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        FragmentScheduleEditorBinding qw0VarC = FragmentScheduleEditorBinding.inflate(layoutInflater, viewGroup, false);
        this.q = qw0VarC;
        return qw0VarC.getRoot();
    }

    @Override // androidx.fragment.app.e
    public void onDestroyView() {
        super.onDestroyView();
        this.q = null;
    }

    @Override // androidx.fragment.app.e
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        this.s = ScheduledMessageDatabase.v(getContext());
        this.z = Calendar.getInstance();
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    setEnabled(false);
                    requireActivity().onBackPressed();
                }
            }
        });
        this.q.editContact.setOnClickListener(view2 -> R());
        this.q.btnAttachImage.setOnClickListener(view2 -> this.Z.launch("image/*"));
        this.q.btnRemoveImage.setOnClickListener(view2 -> A());
        this.q.buttonSave.setOnClickListener(view2 -> T());
        this.q.editMessage.setOnFocusChangeListener((view2, z) -> {
            if (z) {
                this.q.inputMessage.setError(null);
            }
        });
        W();
        X();
        V();
        long j = getArguments() != null ? getArguments().getLong("arg_message_id", -1L) : -1L;
        if (j > 0) {
            this.x = this.s.x(j);
        }
        S();
        FragmentScheduleEditorBinding qw0Var = this.q;
        a0(qw0Var.editDate, qw0Var.editTime);
        b0();
        U();
    }

    public final void y(int i) {
        if (i == 0) {
            return;
        }
        this.q.chipSunday.setChecked((i & 1) != 0);
        this.q.chipMonday.setChecked((i & 2) != 0);
        this.q.chipTuesday.setChecked((i & 4) != 0);
        this.q.chipWednesday.setChecked((i & 8) != 0);
        this.q.chipThursday.setChecked((i & 16) != 0);
        this.q.chipFriday.setChecked((i & 32) != 0);
        this.q.chipSaturday.setChecked((i & 64) != 0);
    }

    public final void z(int i) {
        if (i == 1) {
            this.q.chipRepeatDaily.setChecked(true);
            return;
        }
        if (i == 2) {
            this.q.chipRepeatWeekly.setChecked(true);
            return;
        }
        if (i == 3) {
            this.q.chipRepeatMonthly.setChecked(true);
        } else if (i != 4) {
            this.q.chipRepeatOnce.setChecked(true);
        } else {
            this.q.chipRepeatCustom.setChecked(true);
        }
    }
}
