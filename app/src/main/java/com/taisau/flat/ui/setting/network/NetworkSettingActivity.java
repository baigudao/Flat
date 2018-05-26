package com.taisau.flat.ui.setting.network;

import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.taisau.flat.R;
import com.taisau.flat.listener.OnServerSettingChangeListener;
import com.taisau.flat.ui.BaseActivity;

public class NetworkSettingActivity extends BaseActivity implements INetworkSettingView
        , View.OnClickListener {
    //    private RecyclerView network_setting_list;
//    private ArrayList<String> nameList;
//    private HashMap<String, String> map;
//    private NetworkSettingAdapter networkSettingAdapter;
    private NetworkSettingPresenter networkSettingPresenter;
    private int netType = 0;
    private TextView tvAppVersion, tvIp, tvPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_setting);
        networkSettingPresenter = new NetworkSettingPresenter(this, this);
        initView(networkSettingPresenter.getCurrentAddress());
    }

    private void initView(SparseArray<String> valueList) {
        findViewById(R.id.rl_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_setting_title)).setText(getString(R.string.network_setting));
        tvAppVersion = findViewById(R.id.tv_app_version);
        tvIp = findViewById(R.id.tv_server_ip);
        tvPort = findViewById(R.id.tv_server_port);
        tvIp.setText(valueList.get(0));
        tvPort.setText(valueList.get(1));
        tvAppVersion.setText(valueList.get(4));
        findViewById(R.id.rl_server_ip).setOnClickListener(this);
        findViewById(R.id.rl_server_port).setOnClickListener(this);
//        LinearLayoutManager manager = new LinearLayoutManager(this);
//        network_setting_list = (RecyclerView) findViewById(R.id.setting_list);
//        network_setting_list.setLayoutManager(manager);


//        findViewById(R.id.btn_network_login).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent intent= new Intent();
//                intent.setAction("android.intent.action.VIEW");
//                Uri content_url = Uri.parse("https://www.baidu.com");
//                intent.setData(content_url);
//                startActivity(intent);
//            }
//        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        /* networkSettingAdapter = new NetworkSettingAdapter(this, nameList, valueList);
        network_setting_list.setAdapter(networkSettingAdapter);
//        if (netType==9)
        networkSettingAdapter.setOnItemClickListener(new NetworkSettingAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, final int position) {
//                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                final EditText ip_edit = new EditText(NetworkSettingActivity.this);
                ip_edit.setSingleLine(true);
                ip_edit.setHint(position == 0 || position == 2 ? getString(R.string.ip_format) : getString(R.string.port_format));
                ip_edit.setInputType(InputType.TYPE_CLASS_PHONE);
                new AlertDialog.Builder(NetworkSettingActivity.this).setTitle(getString(R.string.setting) + nameList.get(position))
                        .setMessage(position == 0 || position == 2 ? (getString(R.string.ip_format_message)) : "")
                        .setView(ip_edit)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                networkSettingPresenter.setAddressChange(position, ip_edit.getText().toString());
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
        });*/
//        else
//            Toast.makeText(NetworkSettingActivity.this,"只有以太网才可以设置网络参数", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void changeTextDialog(final int position) {
        String title = "";
        final EditText ip_edit = new EditText(NetworkSettingActivity.this);
        switch (position) {
            case 0:
                title = getString(R.string.ip_server);
                break;
            case 1:
                title = getString(R.string.port_server);
                ip_edit.setInputType(InputType.TYPE_CLASS_PHONE);
                break;

        }
        ip_edit.setSingleLine(true);
        ip_edit.setHint(position == 0 || position == 1 ? getString(R.string.tips_server) : getString(R.string.port_format));
        new AlertDialog.Builder(NetworkSettingActivity.this).setTitle(getString(R.string.setting) + title)
                .setMessage(getString(R.string.ip_format_message))
                .setView(ip_edit)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    networkSettingPresenter.setAddressChange(position, ip_edit.getText().toString());
                    hideSystemUi();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> hideSystemUi())
                .show();
    }

    @Override
    public void showChangeResult(int position, String address) {
        if (address != null) {
            Toast.makeText(NetworkSettingActivity.this, R.string.setting_success, Toast.LENGTH_SHORT).show();
            switch (position){
                case 0:
                    tvIp.setText(address);
                    break;
                case 1:
                    tvPort.setText(address);
                    break;
            }
            OnServerSettingChangeListener.OnSettingChange(address);
        } else {
            Toast.makeText(NetworkSettingActivity.this, R.string.setting_fail, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.rl_server_ip:
                changeTextDialog(0);
                break;
            case R.id.rl_server_port:
                changeTextDialog(1);
                break;
        }
    }
}
