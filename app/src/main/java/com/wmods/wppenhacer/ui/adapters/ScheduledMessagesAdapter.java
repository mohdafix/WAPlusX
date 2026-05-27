package com.wmods.wppenhacer.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.models.ScheduledMessage;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
/* JADX INFO: loaded from: classes.dex */
public class ScheduledMessagesAdapter extends RecyclerView.Adapter<ScheduledMessagesAdapter.c> {
    public final b a;
    public List<ScheduledMessage> b = new ArrayList();

    /* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
    public class a extends DiffUtil.Callback {
        public final /* synthetic */ List a;

        public a(List list) {
            this.a = list;
        }

        @Override // androidx.recyclerview.widget.e.b
        public boolean areContentsTheSame(int i, int i2) {
            ScheduledMessage ScheduledMessageVar = (ScheduledMessage) ScheduledMessagesAdapter.this.b.get(i);
            ScheduledMessage ScheduledMessageVar2 = (ScheduledMessage) this.a.get(i2);
            return ScheduledMessageVar.v() == ScheduledMessageVar2.v() && ScheduledMessageVar.x() == ScheduledMessageVar2.x() && ScheduledMessageVar.r() == ScheduledMessageVar2.r() && ScheduledMessageVar.q() == ScheduledMessageVar2.q() && ScheduledMessageVar.s() == ScheduledMessageVar2.s() && ScheduledMessageVar.t() == ScheduledMessageVar2.t() && Objects.equals(ScheduledMessageVar.n(), ScheduledMessageVar2.n()) && Objects.equals(ScheduledMessageVar.l(), ScheduledMessageVar2.l()) && Objects.equals(ScheduledMessageVar.g(), ScheduledMessageVar2.g());
        }

        @Override // androidx.recyclerview.widget.e.b
        public boolean areItemsTheSame(int i, int i2) {
            return ((ScheduledMessage) ScheduledMessagesAdapter.this.b.get(i)).k() == ((ScheduledMessage) this.a.get(i2)).k();
        }

        @Override // androidx.recyclerview.widget.e.b
        public int getNewListSize() {
            return this.a.size();
        }

        @Override // androidx.recyclerview.widget.e.b
        public int getOldListSize() {
            return ScheduledMessagesAdapter.this.b.size();
        }
    }

    /* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
    public interface b {
        void b(ScheduledMessage ScheduledMessageVar, boolean z);

        void c(ScheduledMessage ScheduledMessageVar);

        void d(ScheduledMessage ScheduledMessageVar);
    }

    /* JADX INFO: compiled from: r8-map-id-7e9c03d778448a8ab9d8bf00d47d602f404db6db93d7a3a72604be4fe8598775 */
    public class c extends RecyclerView.ViewHolder {
        public ImageView A;
        public ImageButton B;
        public ImageButton C;
        public TextView q;
        public TextView s;
        public TextView x;
        public Chip y;
        public SwitchMaterial z;

        public c(View view) {
            super(view);
            this.q = (TextView) view.findViewById(R.id.text_contact_name);
            this.s = (TextView) view.findViewById(R.id.text_message_preview);
            this.x = (TextView) view.findViewById(R.id.text_schedule_time);
            this.y = (Chip) view.findViewById(R.id.chip_status);
            this.z = (SwitchMaterial) view.findViewById(R.id.switch_active);
            this.A = (ImageView) view.findViewById(R.id.icon_has_image);
            this.B = (ImageButton) view.findViewById(R.id.button_edit);
            this.C = (ImageButton) view.findViewById(R.id.button_delete);
            view.setOnClickListener(view2 -> l(view2));
            view.setOnLongClickListener(view2 -> m(view2));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void l(View view) {
            int adapterPosition = getAdapterPosition();
            if (adapterPosition != -1) {
                ScheduledMessagesAdapter.this.a.c((ScheduledMessage) ScheduledMessagesAdapter.this.b.get(adapterPosition));
            }
        }

        public void f(final ScheduledMessage ScheduledMessageVar) {
            Context context = this.itemView.getContext();
            String strG = ScheduledMessageVar.g();
            TextView textView = this.q;
            if (strG == null || strG.isEmpty()) {
                strG = "-";
            }
            textView.setText(strG);
            String strN = ScheduledMessageVar.n();
            TextView textView2 = this.s;
            if (strN == null || strN.isEmpty()) {
                strN = "-";
            }
            textView2.setText(strN);
            this.x.setText(g(context, ScheduledMessageVar));
            this.A.setVisibility(ScheduledMessageVar.u() ? 0 : 8);
            this.z.setOnCheckedChangeListener(null);
            this.z.setChecked(ScheduledMessageVar.v());
            this.z.setEnabled((ScheduledMessageVar.x() && ScheduledMessageVar.r() == 0) ? false : true);
            this.z.setContentDescription(context.getString(ScheduledMessageVar.v() ? R.string.schedule_active : R.string.schedule_inactive));
            this.z.setOnCheckedChangeListener((compoundButton, z) -> i(ScheduledMessageVar, compoundButton, z));
            this.B.setOnClickListener(view -> j(ScheduledMessageVar, view));
            this.C.setOnClickListener(view -> k(ScheduledMessageVar, view));
            if (ScheduledMessageVar.x() && ScheduledMessageVar.r() == 0) {
                this.y.setText(R.string.schedule_sent);
                Chip chip = this.y;
                chip.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_primary));
                Chip chip2 = this.y;
                chip2.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_theme_light_primaryContainer)));
                return;
            }
            if (ScheduledMessageVar.v()) {
                this.y.setText(R.string.schedule_pending);
                Chip chip3 = this.y;
                chip3.setTextColor(android.graphics.Color.parseColor("#424242"));
                Chip chip4 = this.y;
                chip4.setChipBackgroundColor(ColorStateList.valueOf(android.graphics.Color.parseColor("#E0E0E0")));
                return;
            }
            this.y.setText(R.string.schedule_inactive);
            Chip chip5 = this.y;
            chip5.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_error));
            Chip chip6 = this.y;
            chip6.setChipBackgroundColor(ColorStateList.valueOf(android.graphics.Color.parseColor("#FFCDD2")));
        }

        public final String g(Context context, ScheduledMessage ScheduledMessageVar) {
            long jP = (ScheduledMessageVar.r() != 0 && ScheduledMessageVar.v()) ? ScheduledMessageVar.p() : ScheduledMessageVar.s();
            Date date = new Date(jP);
            String str = DateFormat.getTimeFormat(context).format(date);
            if (ScheduledMessageVar.r() != 0) {
                return h(context, ScheduledMessageVar.r()) + " • " + str;
            }
            return DateFormat.getDateFormat(context).format(date) + " • " + str;
        }

        public final String h(Context context, int i) {
            return i != 1 ? i != 2 ? i != 3 ? i != 4 ? context.getString(R.string.schedule_repeat_once) : context.getString(R.string.schedule_repeat_custom) : context.getString(R.string.schedule_repeat_monthly) : context.getString(R.string.schedule_repeat_weekly) : context.getString(R.string.schedule_repeat_daily);
        }

        public final /* synthetic */ void i(ScheduledMessage ScheduledMessageVar, CompoundButton compoundButton, boolean z) {
            ScheduledMessagesAdapter.this.a.b(ScheduledMessageVar, z);
        }

        public final /* synthetic */ void j(ScheduledMessage ScheduledMessageVar, View view) {
            ScheduledMessagesAdapter.this.a.c(ScheduledMessageVar);
        }

        public final /* synthetic */ void k(ScheduledMessage ScheduledMessageVar, View view) {
            ScheduledMessagesAdapter.this.a.d(ScheduledMessageVar);
        }

        public final /* synthetic */ boolean m(View view) {
            int adapterPosition = getAdapterPosition();
            if (adapterPosition == -1) {
                return false;
            }
            ScheduledMessagesAdapter.this.a.d((ScheduledMessage) ScheduledMessagesAdapter.this.b.get(adapterPosition));
            return true;
        }
    }

    public ScheduledMessagesAdapter(b bVar) {
        this.a = bVar;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.h
    /* JADX INFO: renamed from: e, reason: merged with bridge method [inline-methods] */
    public void onBindViewHolder(c cVar, int i) {
        cVar.f(this.b.get(i));
    }

    @Override // androidx.recyclerview.widget.RecyclerView.h
    /* JADX INFO: renamed from: f, reason: merged with bridge method [inline-methods] */
    public c onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new c(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_scheduled_message, viewGroup, false));
    }

    public void g(List<ScheduledMessage> list) {
        ArrayList arrayList = list == null ? new ArrayList() : new ArrayList(list);
        DiffUtil.DiffResult c0092eB = DiffUtil.calculateDiff(new a(arrayList));
        this.b = arrayList;
        c0092eB.dispatchUpdatesTo(this);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.h
    public int getItemCount() {
        return this.b.size();
    }
}
