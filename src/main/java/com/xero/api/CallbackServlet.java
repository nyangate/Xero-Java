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
            createInvoices(client);

        }catch (Exception e){
            e.printStackTrace();
        }
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
                    invoice.setStatus(InvoiceStatus.SUBMITTED);
                    Contact contact=client.getContact(""+data.get("customer_id"));
                    if(contact==null && data.has("customer_phone")){
                        contact=new Contact();
                        contact.setAccountNumber(""+data.get("customer_id"));
                        contact.setContactID(""+data.get("customer_id"));
                        contact.setContactNumber(data.getString("customer_phone"));
                        contact.setFirstName(data.getString("customer_name"));
                        contact.setLastName("");
                        contact.setIsCustomer(true);
                        contact.setName(data.getString("customer_name"));
                    }else{
                        contact.setAccountNumber("1");
                        contact.setName("Default Customer");
                        contact.setContactID("id");
                        contact.setIsCustomer(true);
                        contact.setContactNumber("1");
                    }
                        if(!client.getContacts().contains(contact))
                            client.getContacts().add(contact);


                    invoice.setContact(contact);
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

                                Payment payment=new Payment();
                                payment.setReference(jsonObject.getString("ref"));
                                payment.setDate(calendar);
                                payment.setAmount(BigDecimal.valueOf(jsonObject.getDouble("amount")));
                                payment.setAccount(client.getAccount(getAccountIDD(jsonObject.getString("name"))));
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
                                lineItem.setItemCode(jsonObject.getString("sku"));
                                lineItem.setLineItemID(""+jsonObject.getInt("product_id"));
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