package org.ammlab.android.gcmtest;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.os.AsyncTask;

public class HttpPostAsyncTask extends AsyncTask<String, Integer, Integer> {
	  Context context;
	  
	  public static final String SERVER_URI = "http://ws.ammlab.org:8009";
	  
	  public HttpPostAsyncTask(Context context){
	    this.context = context;
	  }
	  
	  @Override
	  protected Integer doInBackground(String... params) {

      ArrayList<NameValuePair> value = new ArrayList<NameValuePair>();
      String oper =  params[0];
      if(oper.equals("register")){
	      value.add( new BasicNameValuePair("regId", params[1]));
	      value.add( new BasicNameValuePair("Uuid", params[2]));  
	      value.add( new BasicNameValuePair("latitude", params[3]));  
	      value.add( new BasicNameValuePair("longitude", params[4]));  
      }else if (oper.equals("unreg")){
	      value.add( new BasicNameValuePair("Uuid", params[1]));  
      }else{
    	  return 1;
      }

      try {
	      
	      HttpClient httpClient = new DefaultHttpClient();
	      HttpPost httpPost = new HttpPost(SERVER_URI + "/" + oper);
	      ResponseHandler<String> responseHandler = new BasicResponseHandler();
	      httpPost.setEntity(new UrlEncodedFormEntity(value, "UTF-8"));
	      httpClient.execute(httpPost, responseHandler);
	    } catch (ClientProtocolException e) {
	      e.printStackTrace();
	    } catch (IOException e) {
	      e.printStackTrace();
	    }
	    
	    return 0;
	  }

	  @Override
	  protected void onPostExecute(Integer result) {

	  }

	  @Override
	  protected void onPreExecute() {
	  }  

}
