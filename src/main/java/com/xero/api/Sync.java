package com.xero.api;

import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by robertnyangate on 27/03/2017.
 */
public class Sync {

    private FirebaseDatabase getDB() {
        return FirebaseDatabase
                .getInstance();
    }

}
