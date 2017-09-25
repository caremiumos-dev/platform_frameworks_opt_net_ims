/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
        notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.ims;

import java.lang.ref.WeakReference;

import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.telephony.ims.ImsServiceProxy;

import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsEcbmListener;

/**
 * Provides framework interface APIs for managing Emergency Callback Mode for IMS.
 *
 * This object is cleaned up after {@link #onBinderNotAvailable()} is called and the IImsEcbm
 * connection is invalidated.
 * @hide
 */
public class ImsEcbm extends ImsServiceProxy.CallbackManager<ImsEcbmStateListener> {
    private static final String TAG = "ImsEcbm";
    private static final boolean DBG = true;

    private final IImsEcbm miEcbm;
    private ImsEcbmListenerProxy mProxy;
    private EcbmHandler mEcbmHandler;

    private static final int HANDLER_ENTERED_ECBM = 0;
    private static final int HANDLER_EXITED_ECBM = 1;

    // Static to ensure we do not accidentally leak ImsEcbm when it is supposed to be GC'ed
    private static class EcbmHandler extends Handler {

        private final WeakReference<ImsEcbm> mEcbmRef;

        EcbmHandler(ImsEcbm ecbm) {
            mEcbmRef = new WeakReference<>(ecbm);
        }

        @Override
        public void handleMessage(Message msg) {
            ImsEcbm ecbm = mEcbmRef.get();
            if (ecbm == null) {
                return;
            }
            switch(msg.what) {
                case HANDLER_ENTERED_ECBM: {
                    ecbm.notifyCallback(ImsEcbmStateListener::onECBMEntered);
                    break;
                }
                case HANDLER_EXITED_ECBM: {
                    ecbm.notifyCallback(ImsEcbmStateListener::onECBMExited);
                    break;
                }
            }
        }
    }

    public ImsEcbm(IImsEcbm iEcbm) {
        if (DBG) Rlog.d(TAG, "ImsEcbm created");
        miEcbm = iEcbm;
        mEcbmHandler = new EcbmHandler(this);
    }

    public void setEcbmStateListener(ImsEcbmStateListener ecbmListener) throws ImsException {
        addCallback(ecbmListener);
    }

    public void exitEmergencyCallbackMode() throws ImsException {
        try {
            miEcbm.exitEmergencyCallbackMode();
        } catch (RemoteException e) {
            throw new ImsException("exitEmergencyCallbackMode()", e,
                    ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
        }
    }

    public boolean isBinderAlive() {
        return miEcbm != null && miEcbm.asBinder().isBinderAlive();
    }

    @Override
    protected boolean createCallbackBinder() {
        try {
            mProxy = new ImsEcbmListenerProxy(mEcbmHandler);
            miEcbm.setListener(mProxy);
        } catch (RemoteException e) {
            Rlog.e(TAG, "createCallbackBinder: error - " + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    protected void onBinderNotAvailable() {
        mProxy.cleanup();
    }


    /**
     * Adapter class for {@link IImsEcbmListener}.
     *
     * Defined as a static class to ensure that the ImsEcbm class is not kept around until the
     * ECBM listener is garbage collected in another process.
     */
    private static class ImsEcbmListenerProxy extends IImsEcbmListener.Stub {
        private WeakReference<Handler> mHandler;

        public ImsEcbmListenerProxy(Handler handler) {
            mHandler = new WeakReference<>(handler);
        }

        @Override
        public void enteredECBM() {
            if (DBG) Rlog.d(TAG, "enteredECBM ::");

            Handler h = mHandler.get();
            if (h != null) {
                h.obtainMessage(HANDLER_ENTERED_ECBM).sendToTarget();
            }
        }

        @Override
        public void exitedECBM() {
            if (DBG) Rlog.d(TAG, "exitedECBM ::");

            Handler h = mHandler.get();
            if (h != null) {
                h.obtainMessage(HANDLER_EXITED_ECBM).sendToTarget();
            }
        }

        public void cleanup() {
            mHandler.clear();
        }
    }
}
