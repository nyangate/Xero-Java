package com.xero.api;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.xero.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


public class CallbackServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Config config = Config.getInstance();
    public static final SimpleDateFormat properFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public CallbackServlet() {
        super();
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // DEMONSTRATION ONLY - retrieve TempToken from Cookie
        TokenStorage storage = new TokenStorage();

        // retrieve OAuth verifier code from callback URL param
        String verifier = request.getParameter("oauth_verifier");

        // Swap your temp token for 30 oauth token
        OAuthAccessToken accessToken = new OAuthAccessToken(config);
        String storeid=storage.get(request,"storeid");
        accessToken.build(verifier, storage.get(request, "tempToken"), storage.get(request, "tempTokenSecret")).execute();

        if (!accessToken.isSuccess()) {
            storage.clear(response);
            request.getRequestDispatcher("index.jsp").forward(request, response);
        } else {
            System.out.println("Retrieved Token " + accessToken.getToken());
            System.out.println("Retrieved tocken secret " + accessToken.getTokenSecret());

            // DEMONSTRATION ONLY - Store in Cookie - you can extend TokenStorage
            // and implement the save() method for your database
            storage.save(response, accessToken.getAll());
            updateXero(accessToken,storeid);
            request.getRequestDispatcher("callback.jsp").forward(request, response);
        }
    }

    private FirebaseDatabase getDB() {
        return FirebaseDatabase
                .getInstance();
    }

    private void updateXero(OAuthAccessToken accessToken,String storeid) {
        XeroClient client = new XeroClient();
        getDB().getReference().child("xero_tokens").child(storeid).setValue(accessToken.getAll());
        client.setStoreid(storeid);
        client.setOAuthToken(accessToken.getToken(), accessToken.getTokenSecret());
        try {
            createProducts(client);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createProducts(final XeroClient client) {
        try {
            client.getItems().clear();
            if (client.getItems().size() > 0) {
//                if (client.getAccounts().size() < 1) {
//                }
//                if(client.getContacts().size()<1)
                createSuuppliers(client);
                createBankAccounts(client);
                createInvoices(client);
                createReceipts(client);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        getDB().getReference().child(client.getStoreid()).child("products").addListenerForSingleValueEvent(new ValueEventListener
                () {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() == null)
                    return;
                ArrayList<Item> itemsAray = new ArrayList<>();
                for (DataSnapshot product : dataSnapshot.getChildren()) {
                    for (DataSnapshot variant : product.child("variants").getChildren()
                            ) {
                        Item item = new Item();
//                        item.setItemID(""+variant.child("id").getValue()+"-"+
//                                variant.child("id").getValue()+"-"+variant.child("id").getValue()+"-"+variant.child
//                                ("id").getValue()+"-"+variant.child("id").getValue());
                        String name = variant.child("name").getValue() + " - " + variant.child("variant").getValue();
                        item.setCode(variant.child("sku").getValue() + "");
                        item.setName(name.length() > 50 ? "" + variant.child("variant").getValue() : name);
                        item.setDescription(variant.child("varianr").getValue() + "");
                        ItemPriceDetails purchaseDetails = new ItemPriceDetails();
                        purchaseDetails.setUnitPrice(BigDecimal.valueOf(getProduct_RetailPrice(variant.child
                                ("product_cost_price_json").getValue() + "", 1)));
                        item.setPurchaseDetails(purchaseDetails);
                        ItemPriceDetails salepriceDetails = new ItemPriceDetails();
                        salepriceDetails.setUnitPrice(BigDecimal.valueOf(getProduct_RetailPrice(variant.child
                                ("product_selling_price_json").getValue() + "", 1)));
                        item.setSalesDetails(salepriceDetails);
                        item.setIsSold(true);
                        String product_type = variant.child("product_type_id").getValue() + "";
                        if (product_type.equalsIgnoreCase("3")) {
                            item.setIsPurchased(true);
                            item.setQuantityOnHand(BigDecimal.valueOf(getProduct_RetailPrice(variant.child
                                    ("qty_at_hand").getValue() + "", 1)));
//                            item.setIsTrackedAsInventory(true);
                            purchaseDetails.setCOGSAccountCode("310");
                            item.setInventoryAssetAccountCode("630");
                        }

                        itemsAray.add(item);


                    }
                }
                try {
                    client.createItems(itemsAray);
                    System.out.println(">>>>>>added products");
                    createInvoices(client);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public static double getProduct_RetailPrice(String product_selling_price_json, int branch_id) {
        double value = 0.00;
        try {
            JSONObject json = new JSONObject(product_selling_price_json);
            value = json.getDouble("" + branch_id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
    private void createReceipts(final XeroClient client){
        getDB().getReference().child("xero_receipts").child(client.getStoreid()).addValueEventListener(new
                                                                                                           ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                    if(dataSnapshot==null||dataSnapshot.getValue()==null)
                        return;
                    for(DataSnapshot dataSnapshot1:dataSnapshot.getChildren()){
                        try {
                            JSONObject jsonObjectt=new JSONObject(""+dataSnapshot1.getValue());
                            String supplierphone=jsonObjectt.getString("supplier");
                            System.out.print(jsonObjectt);
                            Receipt receipt=new Receipt();

                            Contact contact=client.getContacts(null,"AccountNumber==\""+supplierphone+ "\"",null).get(0);
                            receipt.setContact(contact);
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTime(properFormat.parse(jsonObjectt.getString("date")));

                            receipt.setDate(calendar);
                            receipt.setReference(""+jsonObjectt.getString("reference"));
                            User user=client.getUsers().get(0);
                            receipt.setUser(user);
                            JSONArray jsonArray=new JSONArray(jsonObjectt.getString("items"));
                            ArrayList<LineItem>lineItems=new ArrayList<>();
                            List<Account> accountExpense = client.getAccounts(null,"Type==\"EXPENSE\"",null);
                            for (int i = 0; i < jsonArray.length(); i++) {
                                try {
                                    JSONObject jsonObject=jsonArray.getJSONObject(i);
                                    List<Item> itemslist = client.getItems(null, "Code==\""+jsonObject.getString("sku")+"\"", null);

                                    LineItem lineItem=new LineItem();
                                    lineItem.setItemCode(jsonObject.getString("sku"));
                                    lineItem.setDescription("Same as ordered");
                                    lineItem.setAccountCode(accountExpense.get(0).getCode());
                                    lineItem.setUnitAmount(itemslist.get(0).getPurchaseDetails().getUnitPrice());
                                    lineItem.setQuantity(BigDecimal.valueOf(jsonObject.getDouble("quantity")));
                                    lineItems.add(lineItem);
                                }catch (Exception e){
                                   e.printStackTrace();
                                }


                            }
                            ArrayOfLineItem lineItemss=new ArrayOfLineItem();
                            lineItemss.getLineItem().addAll(lineItems);
                            receipt.setLineItems(lineItemss);
                            receipt.setLineAmountTypes(LineAmountType.INCLUSIVE);
                            ArrayList<Receipt>receipts=new ArrayList<>();
                            receipts.add(receipt);
                            client.createReceipts(receipts);
                            System.out.print("Created receipts");
                            getDB().getReference().child("xero_receipts").child(client.getStoreid()).child(dataSnapshot1.getKey()
                                    .toString())
                                    .removeValue();

                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }
    private void createInvoices(final XeroClient client) {
        getDB().getReference().child("xero_invoices").child(client.getStoreid()).addValueEventListener(new
                                                                                                ValueEventListener() {
                                                                                                    @Override
                                                                                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                                                                                        if (dataSnapshot.getValue() == null)
                                                                                                            return;
                                                                                                        ArrayList<Invoice> invoices = new ArrayList<>();
                                                                                                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                                                                                            try {
                                                                                                                JSONObject data = new JSONObject(snapshot.getValue().toString());
                                                                                                                Invoice invoice = new Invoice();
                                                                                                                invoice.setAmountDue(BigDecimal.valueOf(data.getDouble("paidAmount")));
                                                                                                                invoice.setAmountPaid(BigDecimal.valueOf(data.getDouble("paidAmount")));
                                                                                                                Calendar calendar = Calendar.getInstance();
                                                                                                                calendar.setTime(properFormat.parse(data.getString("date")));
                                                                                                                invoice.setDate(calendar);
                                                                                                                invoice.setDueDate(calendar);
                                                                                                                invoice.setType(InvoiceType.ACCREC);
                                                                                                                invoice.setExpectedPaymentDate(calendar);
                                                                                                                invoice.setInvoiceNumber(data.getString("invoiceNumber"));
                                                                                                                invoice.setReference(data.getString("reference"));
                                                                                                                invoice.setStatus(InvoiceStatus.AUTHORISED);

                                                                                                                if (data.has("customer_email")) {
                                                                                                                    Contact contact = new Contact();
                                                                                                                    contact.setAccountNumber("" + data.get("customer_id"));
                                                                                                                    contact.setContactNumber(data.getString("customer_phone"));
                                                                                                                    contact.setFirstName(data.getString("customer_name"));
                                                                                                                    contact.setLastName("");
                                                                                                                    contact.setIsCustomer(true);
                                                                                                                    contact.setName(data.getString("customer_name"));
                                                                                                                    invoice.setContact(contact);

                                                                                                                } else {
                                                                                                                    Contact contact = new Contact();
                                                                                                                    contact.setAccountNumber("133");
                                                                                                                    contact.setName("Default");
                                                                                                                    contact.setIsCustomer(true);
                                                                                                                    contact.setContactNumber("133");
                                                                                                                    invoice.setContact(contact);
                                                                                                                }


                                                                                                                invoice.setTotal(BigDecimal.valueOf(data.getDouble("total")));

                                                                                                                invoice.setSubTotal(BigDecimal.valueOf(data.getDouble("subtotal")));
                                                                                                                invoice.setTotalTax(BigDecimal.valueOf(data.getDouble("totalTax")));


                                                                                                                JSONArray pays = new JSONArray(data.getString("lines"));
                                                                                                                ArrayOfLineItem arrayoFItemsLines = new ArrayOfLineItem();
                                                                                                                ArrayList<LineItem> lineItems = new ArrayList<>();
                                                                                                                List<Account> accountDirectCosts = client.getAccounts(null, "Type==\"DIRECTCOSTS\"", null);
                                                                                                                for (int i = 0; i < pays.length(); i++) {
                                                                                                                    try {
                                                                                                                        JSONObject jsonObject = pays.getJSONObject(i);
                                                                                                                        LineItem lineItem = new LineItem();
                                                                                                                        lineItem.setDescription(jsonObject.getString("productname"));
                                                                                                                        lineItem.setQuantity(BigDecimal.valueOf(jsonObject.getDouble("quantity")));
                                                                                                                        lineItem.setLineAmount(BigDecimal.valueOf(jsonObject.getDouble("movement_amount")));
                                                                                                                        lineItem.setTaxAmount(BigDecimal.valueOf(jsonObject.getDouble("tax_amount")));
                                                                                                                        lineItem.setDiscountRate(BigDecimal.valueOf(100 * jsonObject.getDouble("discount_amount")
                                                                                                                                / jsonObject.getDouble("movement_amount")));
                                                                                                                        lineItem.setAccountCode(accountDirectCosts.get(0).getCode());
                                                                                                                        lineItem.setUnitAmount(BigDecimal.valueOf(jsonObject.getDouble("selling_price")));
                                                                                                                        lineItem.setTaxType("OUTPUT");
                                                                                                                        lineItems.add(lineItem);
                                                                                                                    } catch (Exception e) {
                                                                                                                        e.printStackTrace();
                                                                                                                    }
                                                                                                                }
                                                                                                                arrayoFItemsLines.getLineItem().addAll(lineItems);
                                                                                                                invoice.setLineItems(arrayoFItemsLines);
                                                                                                                invoice.setLineAmountTypes(LineAmountType.INCLUSIVE);
                                                                                                                invoices.add(invoice);
                                                                                                                client.createInvoices(invoices);
                                                                                                                System.out.println(">>>>>>added invoice");
                                                                                                                invoices.clear();

                                                                                                                ArrayOfPayment array_ofPayments = new ArrayOfPayment();
                                                                                                                ArrayList<Payment> payments = new ArrayList<>();
                                                                                                                pays = new JSONArray(data.getString("payments"));
                                                                                                                for (int i = 0; i < pays.length(); i++) {
                                                                                                                    JSONObject jsonObject = pays.getJSONObject(i);
                                                                                                                    if (jsonObject.has("amount") && jsonObject.getDouble("amount") > 0) {
                                                                                                                        try {
                                                                                                                            System.out.print(jsonObject.get("name"));
                                                                                                                            List<Account> accountWhere = client.getAccounts(null,
                                                                                                                                    "BankAccountNumber==\"" + getAccountIDD(jsonObject
                                                                                                                                            .getString("name")) + "\"", null);

                                                                                                                            Payment payment = new Payment();
                                                                                                                            payment.setReference(jsonObject.getString("ref"));
                                                                                                                            payment.setDate(calendar);
                                                                                                                            payment.setAmount(BigDecimal.valueOf(jsonObject.getDouble("amount")));
//                                Account account=new Account();
//                                account.setCode(accountWhere.get(0));
                                                                                                                            payment.setAccount(accountWhere.get(0));
                                                                                                                            payment.setStatus(PaymentStatus.AUTHORISED);
                                                                                                                            payment.setInvoice(invoice);
                                                                                                                            payments.add(payment);
                                                                                                                        } catch (Exception e) {
                                                                                                                            System.out.println("payment error ><<<<<" + e.getLocalizedMessage());
                                                                                                                            e.printStackTrace();
                                                                                                                        }

                                                                                                                    }
                                                                                                                }
                                                                                                                array_ofPayments.getPayment().addAll(payments);
                                                                                                                client.createPayments(payments);
                                                                                                                getDB
                                                                                                                        ().getReference().child("xero_invoices").child(client.getStoreid()).child(invoice.getReference()).removeValue();
                                                                                                                System.out.println(">>>>>>added payment");
                                                                                                            } catch (Exception e) {
                                                                                                                e.printStackTrace();
                                                                                                            }
                                                                                                        }

                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onCancelled(DatabaseError databaseError) {

                                                                                                    }
                                                                                                });
    }

    private void createSuuppliers(final XeroClient client) {
        getDB().getReference().child(client.getStoreid()).child("suppliers").addValueEventListener(new
                                                                                            ValueEventListener() {
                                                                                                @Override
                                                                                                public void onDataChange(DataSnapshot dataSnapshot) {
                                                                                                    if (dataSnapshot.getValue() == null)
                                                                                                        return;
                                                                                                    ArrayList<Contact> contacts = new ArrayList<>();
                                                                                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {

                                                                                                        try {
                                                                                                            if(client
                                                                                                                    .getContacts(null,"AccountNumber==\""+snapshot.child
                                                                                                                    ("phone").getValue().toString() + "\"",null).size()>0)
                                                                                                                break;
                                                                                                            Contact contact = new Contact();
                                                                                                            contact
                                                                                                                    .setAccountNumber("" + snapshot.child("phone").getValue());
                                                                                                            contact
                                                                                                                    .setContactNumber(""+snapshot.child("phone").getValue());
                                                                                                            contact.setIsCustomer(false);
                                                                                                            contact.setIsSupplier(true);
                                                                                                            contact
                                                                                                                    .setEmailAddress(""+snapshot.child("email").getValue());
                                                                                                            contact
                                                                                                                    .setName(""+snapshot.child("name").getValue());
                                                                                                            ArrayOfAddress address=new ArrayOfAddress();
                                                                                                            Address
                                                                                                                    address1=new Address();
                                                                                                            address1.setAddressLine1(""+snapshot.child("address").getValue());
                                                                                                            address1.setAddressType(AddressType.STREET);
                                                                                                            address
                                                                                                                    .getAddress().add(address1);
                                                                                                            contact
                                                                                                                    .setAddresses(address);
                                                                                                            contacts.add(contact);
                                                                                                            client.createContact(contacts);
                                                                                                            contacts.clear();


                                                                                                            System
                                                                                                                    .out.println(">>>>>>added supplier");
                                                                                                        } catch (Exception e) {
                                                                                                            e.printStackTrace();
                                                                                                        }
                                                                                                    }

                                                                                                }

                                                                                                @Override
                                                                                                public void onCancelled(DatabaseError databaseError) {

                                                                                                }
                                                                                            });
    }

    private void createBankAccounts(XeroClient client) {
        String[] accounts = new String[]{"MCASH", "CREDIT", "VISA", "LOYALTY", "VOUCHER", "CASH"};
        ArrayList<Account> bankAccounts = new ArrayList<>();

        for (String accountname : accounts) {
            try {
                String accountNumber = getAccountIDD(accountname);
                List<Account> accountWhere = client.getAccounts(null,
                        "BankAccountNumber==\"" + accountNumber + "\"", null);
                if (accountWhere.size() > 0)
                    break;
                Account account = new Account();
                account.setCode(accountNumber.substring(0, 3));
                account.setBankAccountNumber(accountNumber);
                account.setBankAccountType(BankAccountType.BANK);
                account.setCurrencyCode(CurrencyCode.KES);
                account.setDescription(accountname.toLowerCase() + " Payments");
                account.setEnablePaymentsToAccount(true);
                account.setName(accountname);
                account.setStatus(AccountStatus.ACTIVE);
                account.setType(AccountType.BANK);
                bankAccounts.add(account);
                client.createAccounts(bankAccounts);
                bankAccounts.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    private String getAccountIDD(String name) {
        switch (name) {

            case "MCASH":
                return "1209091";
            case "CREDIT":
                return "1229093";
            case "VISA":
                return "1249094";
            case "LOYALTY":
                return "1259095";
            case "VOUCHER":
                return "1269096";
            default:
                return "1219091";
        }
    }


}