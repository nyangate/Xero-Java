package com.xero.api;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

public class TokenStorage 
{
	
	public  TokenStorage() 
	{

	}

	public String get(HttpServletRequest request,String key)
	{
		String item = null;
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (int i = 0; i < cookies.length; i++) 
			{
				if (cookies[i].getName().equals(key)) 
				{
					item = cookies[i].getValue();
				}
			}
		}
		return item;
	}
	
	public boolean tokenIsNull(String token) {
		if (token != null && !token.isEmpty()) { 
			return false;
		} else {
			return true;
		}
	}

	public void clear(HttpServletResponse response)
	{
		HashMap<String,String> map = new HashMap<String,String>();

		map.put("tempToken","");
		map.put("tempTokenSecret","");
		map.put("sessionHandle","");
		map.put("tokenTimestamp","");

		save(response,map);
	}

	public void save(HttpServletResponse response,HashMap<String,String> map)
	{
		Set<Entry<String, String>> set = map.entrySet();
		Iterator<Entry<String, String>> iterator = set.iterator();
		System.out.println("Saving tokens");

		while(iterator.hasNext()) {
			Entry<?, ?> mentry = iterator.next();
			String key = (String)mentry.getKey();
			String value = (String)mentry.getValue();
			System.out.println("Saving key "+key);
			System.out.println("Saving value "+value);

			//System.out.print("key is: "+ key + " & Value is: " + value);

			Cookie t = new Cookie(key,value);
			response.addCookie(t);
		}
	}
}