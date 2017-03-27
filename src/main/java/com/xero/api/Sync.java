package com.xero.api;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Created by robertnyangate on 27/03/2017.
 */
public class Sync {

    private static FirebaseDatabase getDB() {
        return FirebaseDatabase
                .getInstance();
    }
    public static void sync(){
        updateInvoices();
        updateReceipts();
    }

    public static void updateInvoices(){
        getDB().getReference().child("xero_invoices").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //todo find if the storeid belongs to an organization already in xero
                //todo update invoices on xero
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
    public static void updateReceipts(){
        getDB().getReference().child("xero_receipts").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //todo find if the storeid belongs to an organization already in xero
                //todo update invoices on xero
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

}
