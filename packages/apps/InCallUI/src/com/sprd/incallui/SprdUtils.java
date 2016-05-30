/*
 * Copyright (C) 2013 Spreadtrum Communications Inc.
 *
 */

package com.sprd.incallui;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.SystemProperties;
import android.telecom.TelecomManager;

public class SprdUtils {

    public  static boolean getKeyGuardStatus(Context context){
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if(keyguardManager !=null && keyguardManager.inKeyguardRestrictedInputMode()){
            return true;
        }else{
            return false;
        }
    }
    /**
     * SPRD bug 406334
     * @param context
     * @return
     *{@/*/
    public  static TelecomManager getTelecommService(Context context) {
        return (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }
/**@}*/
}
