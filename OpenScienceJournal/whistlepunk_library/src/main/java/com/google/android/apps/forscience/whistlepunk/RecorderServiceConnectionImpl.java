/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;

import java.util.LinkedList;
import java.util.Queue;

class RecorderServiceConnectionImpl implements ServiceConnection, RecorderServiceConnection {
    private final FailureListener mOnFailure;
    private Queue<FallibleConsumer<RecorderService>> mOperations = new LinkedList<>();
    private RecorderService mService;

    public RecorderServiceConnectionImpl(Context context, FailureListener onFailure) {
        mOnFailure = onFailure;
        context.bindService(new Intent(context, RecorderService.class), this,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        RecorderService.Binder binder = (RecorderService.Binder) service;
        mService = binder.getService();
        while (!mOperations.isEmpty()) {
            runOperation(mOperations.remove());
        }
    }

    private void runOperation(FallibleConsumer<RecorderService> op) {
        try {
            op.take(mService);
        } catch (Exception e) {
            mOnFailure.fail(e);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }

    @Override
    public void runWithService(FallibleConsumer<RecorderService> c) {
        if (mService != null) {
            runOperation(c);
        } else {
            mOperations.add(c);
        }
    }
}
