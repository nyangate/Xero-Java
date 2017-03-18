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


public class CallbackServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	private Config config = Config.getInstance();
    public static final SimpleDateFormat properFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public CallbackServlet()
	{
		super();
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		// DEMONSTRATION ONLY - retrieve TempToken from Cookie
		TokenStorage storage = new TokenStorage();

		// retrieve OAuth verifier code from callback URL param
		String verifier = request.getParameter("oauth_verifier");

		// Swap your temp token for 30 oauth token
		OAuthAccessToken accessToken = new OAuthAccessToken(config);
		accessToken.build(verifier,storage.get(request,"tempToken"),storage.get(request,"tempTokenSecret")).execute();

		if(!accessToken.isSuccess())
		{
			storage.clear(response);
			request.getRequestDispatcher("index.jsp").forward(request, response);
		}
		else
		{
            System.out.println("Retrieved Token "+accessToken.getToken());
            System.out.println("Retrieved tocken secret "+accessToken.getTokenSecret());

			// DEMONSTRATION ONLY - Store in Cookie - you can extend TokenStorage
			// and implement the save() method for your database
			storage.save(response,accessToken.getAll());
			updateXero(accessToken);
			request.getRequestDispatcher("callback.jsp").forward(request, response);
		}
	}

    private FirebaseDatabase getDB() {
        return FirebaseDatabase
                .getInstance();
    }

    private void updateXero(OAuthAccessToken accessToken) {
        XeroClient client = new XeroClient();
        client.setOAuthToken(accessToken.getToken(), accessToken.getTokenSecret());
        try{
            createProducts(client);

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void createProducts(final XeroClient client){
        try {
            client.getItems().clear();
            if(client.getItems().size()>0){
                createInvoices(client);
                return;
}
        } catch (IOException e) {
            e.printStackTrace();
        }
        getDB().getReference().child("17").child("products").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.getValue()==null)
                    return;
                ArrayList<Item>itemsAray=new ArrayList<>();
                for(DataSnapshot product:dataSnapshot.getChildren()){
                    for (DataSnapshot variant:product.child("variants").getChildren()
                         ) {
                        Item item=new Item();
//                        item.setItemID(""+variant.child("id").getValue()+"-"+
//                                variant.child("id").getValue()+"-"+variant.child("id").getValue()+"-"+variant.child
//                                ("id").getValue()+"-"+variant.child("id").getValue());
                        item.setCode(variant.child("sku").getValue()+"");
                        item.setName(variant.child("name").getValue()+" - "+variant.child("variant").getValue());
                        item.setDescription(variant.child("varianr").getValue()+"");
                        ItemPriceDetails priceDetails=new ItemPriceDetails();
                        priceDetails.setUnitPrice(BigDecimal.valueOf(getProduct_RetailPrice(variant.child
                                ("product_selling_price_json")+"",1)));
                        item.setPurchaseDetails(priceDetails);
                        item.setIsPurchased(true);
                        item.setIsSold(true);
//                        item.setInventoryAssetAccountCode("310");
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
        }
        return value;
    }

    private void createInvoices(final XeroClient client) {
	    getDB().getReference().child("xero_invoices").child("17").addListenerForSingleValueEvent(new
                                                                                                        ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.getValue()==null)
                    return;
                ArrayList<Invoice>invoices=new ArrayList<>();
                for(DataSnapshot snapshot:dataSnapshot.getChildren()){
                    try{
                    JSONObject data=new JSONObject(snapshot.getValue().toString());
                    Invoice invoice=new Invoice();
                    invoice.setAmountDue(BigDecimal.valueOf(data.getDouble("paidAmount")));
                    invoice.setAmountPaid(BigDecimal.valueOf(data.getDouble("paidAmount")));
                    Calendar calendar=Calendar.getInstance();
                    calendar.setTime(properFormat.parse(data.getString("date")));
                    invoice.setDate(calendar);
                    invoice.setDueDate(calendar);
                    invoice.setType(InvoiceType.ACCREC);
                    invoice.setExpectedPaymentDate(calendar);
                    invoice.setInvoiceNumber(data.getString("invoiceNumber"));
                    invoice.setReference(data.getString("reference"));
                    invoice.setStatus(InvoiceStatus.AUTHORISED);

                    if(data.has("customer_email")){
                        Contact contact=new Contact();
                        contact.setAccountNumber(""+data.get("customer_id"));
                        contact.setContactNumber(data.getString("customer_phone"));
                        contact.setFirstName(data.getString("customer_name"));
                        contact.setLastName("");
                        contact.setIsCustomer(true);
                        contact.setName(data.getString("customer_name"));
                        invoice.setContact(contact);

                    }else{
                        Contact contact=new Contact();
                        contact.setAccountNumber("133");
                        contact.setName("Default");
                        contact.setIsCustomer(true);
                        contact.setContactNumber("133");
                        invoice.setContact(contact);
                    }




                    invoice.setTotal(BigDecimal.valueOf(data.getDouble("total")));
                    invoice.setSubTotal(BigDecimal.valueOf(data.getDouble("subtotal")));
                    invoice.setTotalTax(BigDecimal.valueOf(data.getDouble("totalTax")));
                        ArrayOfPayment array_ofPayments=new ArrayOfPayment();
                        ArrayList<Payment>payments=new ArrayList<>();
                        JSONArray pays=new JSONArray(data.getString("payments"));
                        for (int i = 0; i < pays.length(); i++) {
                            JSONObject jsonObject=pays.getJSONObject(i);
                            if(jsonObject.has("amount") && jsonObject.getDouble("amount")>0){
                                try{
                                    List<Account> accountWhere = client.getAccounts(null,"Code==\""+getAccountIDD(jsonObject.getString("name"))+"\"",null);

                                Payment payment=new Payment();
                                payment.setReference(jsonObject.getString("ref"));
                                payment.setDate(calendar);
                                payment.setAmount(BigDecimal.valueOf(jsonObject.getDouble("amount")));
//                                Account account=new Account();
//                                account.setCode(accountWhere.get(0));
                                payment.setAccount(accountWhere.get(0));
                                payment.setStatus(PaymentStatus.AUTHORISED);
                                payments.add(payment);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }

                            }
                        }
                        array_ofPayments.getPayment().addAll(payments);
                        invoice.setPayments(array_ofPayments);

                        pays=new JSONArray(data.getString("lines"));
                        ArrayOfLineItem arrayoFItemsLines=new ArrayOfLineItem();
                        ArrayList<LineItem>lineItems=new ArrayList<>();
                        List<Account> accountDirectCosts = client.getAccounts(null,"Type==\"DIRECTCOSTS\"",null);
                        for (int i = 0; i < pays.length(); i++) {
                            try {
                                JSONObject jsonObject=pays.getJSONObject(i);
                                LineItem lineItem=new LineItem();
                                lineItem.setDescription(jsonObject.getString("productname"));
                                lineItem.setQuantity(BigDecimal.valueOf(jsonObject.getDouble("quantity")));
                                lineItem.setLineAmount(BigDecimal.valueOf(jsonObject.getDouble("movement_amount")));
                                lineItem.setTaxAmount(BigDecimal.valueOf(jsonObject.getDouble("tax_amount")));
                                lineItem.setDiscountRate(BigDecimal.valueOf(100*jsonObject.getDouble("discount_amount")
                                        /jsonObject.getDouble("movement_amount")));
                                lineItem.setAccountCode(accountDirectCosts.get(0).getCode());
                                lineItem.setUnitAmount(BigDecimal.valueOf(jsonObject.getDouble("selling_price")));
                                lineItems.add(lineItem);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                        arrayoFItemsLines.getLineItem().addAll(lineItems);
                        invoice.setLineItems(arrayoFItemsLines);
                        invoices.add(invoice);
                        System.out.println(">>>>>>added invoice");
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
                try {
                    client.createInvoices(invoices);
                    getDB().getReference().child("xero_invoices").child("17").removeValue();
                }catch (Exception e){
//                    e.printStackTrace();
                    System.out.println(e.getLocalizedMessage());
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private String getAccountIDD(String name) {
	    switch (name){

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