/*
 * Copyright (C) 2015 Andriy Druk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.druk.bonjourbrowser.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.druk.bonjourbrowser.Config;
import com.druk.bonjourbrowser.entity.BonjourService;
import com.druk.bonjourbrowser.ui.RegTypeActivity;
import com.druk.bonjourbrowser.ui.adapter.ServiceAdapter;

import java.util.Collection;
import java.util.Iterator;

public class RegTypeBrowserFragment extends ServiceBrowserFragment {

    public static Fragment newInstance(String regType){
        return fillArguments(new RegTypeBrowserFragment(), Config.EMPTY_DOMAIN, regType);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new ServiceAdapter(getActivity())
        {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                final BonjourService service = getItem(i);
                viewHolder.domain.setText(service.serviceName);
                viewHolder.serviceCount.setText(service.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT) + " services");

                viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Context context = v.getContext();
                        String[] regTypeParts = service.getRegTypeParts();
                        String reqType = service.serviceName + "." +  regTypeParts[0] + ".";
                        String domain = regTypeParts[1] + ".";
                        RegTypeActivity.startActivity(context, reqType, domain);
                    }
                });
            }
        };
    }

    @Override
    protected void filter(Collection<BonjourService> services) {
        Iterator<BonjourService> i = services.iterator();
        while (i.hasNext()) {
            if (!i.next().dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) {
                i.remove();
            }
        }
    }
}
