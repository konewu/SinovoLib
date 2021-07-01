package com.sinovotec.sinovolibdemo.ui.home;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.qmuiteam.qmui.widget.roundwidget.QMUIRoundButton;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovolibdemo.MyApp;
import com.sinovotec.sinovolibdemo.R;

public class HomeFragment extends Fragment {
    private final String TAG = "SinovoLib";
    private EditText qrcode_et = null;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_home, container, false);

        MyApp.getInstance().setShowStatus(root.findViewById(R.id.statusTX));
        qrcode_et = root.findViewById(R.id.qrcodeET);
        final QMUIRoundButton  connect1_btn = root.findViewById(R.id.connect1);
        MyApp.getInstance().setLockBtn(root.findViewById(R.id.connect2));
        MyApp.getInstance().setShowResult(root.findViewById(R.id.showresult));
        MyApp.getInstance().setCodeET(root.findViewById(R.id.codeET));
        MyApp.getInstance().setShowlockType(root.findViewById(R.id.firwwareet));
        MyApp.getInstance().setShowlockver(root.findViewById(R.id.versionet));
        MyApp.getInstance().setShowlockpower(root.findViewById(R.id.poweret));

        final QMUIRoundButton btn_chkpw = root.findViewById(R.id.chkpower);

        connect1_btn.setOnClickListener(view -> {
            if (qrcode_et.getText().length() != 12){
                Toast.makeText(getActivity(), getResources().getString(R.string.qrcode_len), Toast.LENGTH_SHORT).show();
                return;
            }

            String qrcode = qrcode_et.getText().toString();
            showProgressDialog("Connecting....");
            SinovoBle.getInstance().connectLockViaQRCode(qrcode);
        });


        MyApp.getInstance().getLockBtn().setOnClickListener(view -> {
            String code = MyApp.getInstance().getCodeET().getText().toString();
            if (code.isEmpty()){
                Toast.makeText(getActivity(), getResources().getString(R.string.inputcode), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SinovoBle.getInstance().isBleConnected()){
                Toast.makeText(getActivity(), getResources().getString(R.string.unconnect), Toast.LENGTH_SHORT).show();
                return;
            }

            String openType = "01";
            if (MyApp.getInstance().getLockStatus().equals("01")){
                openType = "00";
            }

            MyApp.getInstance().setCode(code);
            SinovoBle.getInstance().toUnlock(openType, code, MyApp.getInstance().getLockSno());
        });


        btn_chkpw.setOnClickListener(view -> {
            if (!SinovoBle.getInstance().isBleConnected()){
                Toast.makeText(getActivity(), getResources().getString(R.string.unconnect), Toast.LENGTH_SHORT).show();
                return;
            }
            SinovoBle.getInstance().getLockInfo("01", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("02", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("03", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("04", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("05", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("06", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("07", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("08", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("09", MyApp.getInstance().getLockSno());
            SinovoBle.getInstance().getLockInfo("10", MyApp.getInstance().getLockSno());

        });

        return root;
    }

    //显示 进度dialog
    private void showProgressDialog(String msg){
        if (MyApp.getInstance().getProgressDialog() == null) {
            MyApp.getInstance().setProgressDialog(new ProgressDialog(MyApp.getInstance().getMyContext()));
            MyApp.getInstance().getProgressDialog().setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }
        MyApp.getInstance().getProgressDialog().setMessage(msg);
        MyApp.getInstance().getProgressDialog().setCancelable(true);
        MyApp.getInstance().getProgressDialog().show();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Home onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Home onDestroy");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Home onResume");
        if (SinovoBle.getInstance().isBleConnected()){
            MyApp.getInstance().getShowStatus().setText("Connected");
            MyApp.getInstance().getShowStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(),R.color.colorBackground_green));
        }

        MyApp.getInstance().getCodeET().setText(MyApp.getInstance().getCode());
        MyApp.getInstance().getShowlockpower().setText(MyApp.getInstance().getLockpower());
        MyApp.getInstance().getShowlockver().setText(MyApp.getInstance().getLockversion());
        MyApp.getInstance().getShowlockType().setText(MyApp.getInstance().getLocktype());

        if (qrcode_et!=null){
            qrcode_et.setText(MyApp.getInstance().getQrcode_input());
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "Home onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "Home onStop");
    }
}