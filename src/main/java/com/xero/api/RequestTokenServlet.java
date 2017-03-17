package com.xero.api;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet implementation class RequestTokenServlet
 */
public class RequestTokenServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private Config config = Config.getInstance();   

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public RequestTokenServlet() {
		super();
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		// IF Xero App is Private > 2 legged oauth - fwd to RequestResouce Servlet
		if(config.getAppType().equals("PRIVATE")) {
			response.sendRedirect("./callback.jsp");
		} else {

			OAuthRequestToken requestToken = new OAuthRequestToken(config);
			requestToken.execute();

			// DEMONSTRATION ONLY - Store in Cookie - you can extend TokenStorage
			// and implement the save() method for your database
			TokenStorage storage = new TokenStorage();
			storage.save(response,requestToken.getAll());

			//Build the Authorization URL and redirect User
			OAuthAuthorizeToken authToken = new OAuthAuthorizeToken(requestToken.getTempToken());
			response.sendRedirect(authToken.getAuthUrl());	

		}
	}
}
