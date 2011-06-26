/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package org.herrlado.websms.connector.smsge;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.WebSMSException;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;


/**
 * Receives commands coming as broadcast from WebSMS.
 * 
 * @author lado
 */
public class ConnectorSmsge extends Connector {
	/** Tag for debug output. */
	private static final String TAG = "WebSMS.sms.ge";

	/** Login URL, to send Login (POST). */
	private static final String LOGIN_URL = "http://www.sms.ge/ngeo/index.php";

	/** Send SMS URL(POST) / Free SMS Count URL(GET). */
	private static final String SMS_URL = "http://www.sms.ge/ngeo/send.php";

	/** Encoding to use. */
	private static final String ENCODING = "UTF-8";

	/** HTTP Header User-Agent. */
	private static final String FAKE_USER_AGENT = "Mozilla/5.0 (Windows; U;"
			+ " Windows NT 5.1; de; rv:1.9.0.9) Gecko/2009040821"
			+ " Firefox/3.0.9 (.NET CLR 3.5.30729)";

	/** This String will be matched if the user is logged in. */
	private static final String MATCH_LOGIN_SUCCESS = "logout.php";

	private static final String URL_CAPTCHA = "http://www.sms.ge/ngeo/inc/include/securimage/securimage_show.php";
	/** Object to sync with. */
	private static final Object CAPTCHA_SYNC = new Object();
	
	/** Timeout for entering the captcha. */
	private static final long CAPTCHA_TIMEOUT = 60000;

	/** Solved Captcha. */
	private static String captchaSolve = null;
	
	private static final String CHECK_WRONGCAPTCHA = "უსაფრთხოების კოდი არასწორია";
	private static final String CHECK_WRONG_NUMBER = "ტელეფონის ნომერი რომელიც თქვენ შეიყვანეთ არასწორია";
	private static final String CHECK_SUCCESS = "თქვენი შეტყობინება წარმატებით გაიგზავნა";
	private static final String CHECK_NO_GEOCELL = "მითითებული ნომერი არ ეკუთვნის ჯეოსელს";
    private static final String PARAM_uid ="bG%8C%0B%F6%A4%2AO";
    private static final String PARAM_sys = "5%90%EC%EF%C3q%F4%ED%7B%B9%ADc%15%92%AE%93";
    private static final String PARAM_x = "31";
    private static final String PARAM_y = "17";
    private static final String PARAM_Send = "1";
    //private static final String PARAM_captcha_code="";
    //private static final String PARAM_message="";
    //private static final String PARAM_num="";
    //private static final String PRAM_phone="";
    //private static final String PARAM_geolai="";
    
    
		/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.smsge_name);
		final ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(// .
				context.getString(R.string.smsge_author));
		c.setBalance(null);
		//c.setPrefsTitle(context.getString(R.string.preferences));

		c.setCapabilities(ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND
				| ConnectorSpec.CAPABILITIES_PREFS);
		c.addSubConnector(TAG, c.getName(),
				SubConnectorSpec.FEATURE_CUSTOMSENDER);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.ENABLED, false)) {
			if (p.getString(Preferences.PASSWORD, "").length() > 0) {
				connectorSpec.setReady();
			} else {
				connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
			}
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		return connectorSpec;
	}

	/**
	 * b This post data is needed for log in.
	 * 
	 * @param username
	 *            username
	 * @param password
	 *            password
	 * @return array of params
	 * @throws UnsupportedEncodingException
	 *             if the url can not be encoded
	 */
	private static String getLoginPost(final String username,
			final String password) throws UnsupportedEncodingException {
		final StringBuilder sb = new StringBuilder();
		sb.append("userid=");
		sb.append(URLEncoder.encode(username, ENCODING));
		sb.append("&passwd=");
		sb.append(URLEncoder.encode(password, ENCODING));
		sb.append("&" + URLEncoder.encode("submit.x") + "=42");
		sb.append("&" + URLEncoder.encode("submit.y") + "=5");
		//Log.d(TAG, sb.toString());
		return sb.toString();
	}

	private Pair<String, String> normalizeNumber(String number){
		if(number.startsWith("00995")){
			number = number.substring(5);
		} else if(number.startsWith("+995")){
			number = number.substring(4);
		}
		
		if(number.startsWith("5") == false){
			throw new WebSMSException("Not a valid recipient!");
		}
		
		return Pair.create("995"+number.substring(0,3), number.substring(3));
	}
	
	/**
	 * These post data is needed for sending a sms.
	 * 
	 * @param ctx
	 *            {@link ConnectorContext}s
	 * @return array of params
	 * @throws Exception
	 *             if an error occures.
	 */
	private String getSmsPost(final ConnectorContext ctx, String captcha) throws Exception {

		final String[] to = ctx.getCommand().getRecipients();
		if(to.length != 1){
			throw new WebSMSException("Only one Recipient allowed");
		}

		
		String msg = ctx.getCommand().getText();
		if(msg.length() > 150){
			throw new WebSMSException("Message text too long. Max. 150 chars allowed!");
		}
		
		Pair<String,String> number = normalizeNumber(Utils.getRecipientsNumber(to[0]));
			
		StringBuilder sb1 = new StringBuilder();
		sb1.append("geolai").append("=").append(number.first);
		sb1.append("&");
		sb1.append("phone").append("=").append(number.second);
		sb1.append("&");
		sb1.append("message").append("=").append(e(msg));
		sb1.append("&");
		sb1.append("num").append("=").append(msg.length());
		sb1.append("&");
		sb1.append("uid").append("=").append(e(PARAM_uid));
		sb1.append("&");
		sb1.append("sys").append("=").append(e(PARAM_sys));
		sb1.append("&");
		sb1.append("Send").append("=").append(e(PARAM_Send));
		sb1.append("&");
		sb1.append("x").append("=").append(PARAM_x);
		sb1.append("&");
		sb1.append("y").append("=").append(PARAM_y);
		sb1.append("&");
		sb1.append("captcha_code").append("=").append(captcha);


		final String post = sb1.toString();
		Log.d(TAG, "request: " + post);
		
		
		return post;
	}
	
	private String e(String toenc) throws UnsupportedEncodingException{
		return URLEncoder.encode(toenc,ENCODING);
	}

	/**
	 * Login to arcor.
	 * 
	 * @param ctx
	 *            {@link ConnectorContext}
	 * @return true if successfullu logged in, false otherwise.
	 * @throws WebSMSException
	 *             if any Exception occures.
	 */
	private boolean login(final ConnectorContext ctx) throws WebSMSException {
		try {

			final SharedPreferences p = ctx.getPreferences();
			final HttpPost request = createPOST(LOGIN_URL);
			request.addHeader("Referer", "http://www.sms.ge/ngeo/index.php");
			 String post = getLoginPost(p
						.getString(Preferences.USERNAME, ""), p.getString(
						Preferences.PASSWORD, ""));
			request.setEntity(new StringEntity(post));
			final HttpResponse response = ctx.getClient().execute(request);
			// response = ctx.getClient().execute(
			// new HttpGet("http://www.sms.ge/ngeo/main.php"));
			final String cutContent = Utils.stream2str(response.getEntity()
					.getContent());

			Log.d(TAG, cutContent);
			if (cutContent.indexOf(MATCH_LOGIN_SUCCESS) == -1) {
				throw new WebSMSException(ctx.getContext(), R.string.error_pw);
			}

			// notifyFreeCount(ctx, cutContent);

		} catch (final Exception e) {
			throw new WebSMSException(e.getMessage());
		}
		return true;
	}

	/**
	 * Create and Prepare a Post Request. Set also an User-Agent
	 * 
	 * @param url
	 *            http post url
	 * @param urlencodedparams
	 *            key=value pairs as url encoded string
	 * @return HttpPost
	 * @throws Exception
	 *             if an error occures
	 */
	private static HttpPost createPOST(final String url) throws Exception {
		final HttpPost post = new HttpPost(url);
		post.setHeader("User-Agent", FAKE_USER_AGENT);
		post.setHeader(new BasicHeader(HTTP.CONTENT_TYPE,
				URLEncodedUtils.CONTENT_TYPE));
		return post;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent)
			throws WebSMSException {
		final ConnectorContext ctx = ConnectorContext.create(context, intent);
		final String term = "\u221E";
		this.getSpec(ctx.getContext()).setBalance(term);
	}

	/**
	 * Sends an sms via HTTP POST.
	 * 
	 * @param ctx
	 *            {@link ConnectorContext}
	 * @return successfull?
	 * @throws WebSMSException
	 *             on an error
	 */
	private boolean sendSms(final ConnectorContext ctx, String captcha) throws WebSMSException {
		try {
			HttpPost post = createPOST(SMS_URL);
			String p = getSmsPost(ctx, captcha);
			post.setEntity(new StringEntity(p));
			final HttpResponse response = ctx.getClient().execute(post);
			final boolean sent = this.afterSmsSent(ctx, response);
			return sent;
		} catch (WebSMSException ex){
			throw ex;
		} catch (final Exception ex) {
			throw new WebSMSException(ex.getMessage());
		}
	}

	/**
	 * Handles content after sms sending.
	 * 
	 * @param ctx
	 *            {@link ConnectorContext}
	 * @param response
	 *            HTTP Response
	 * @return true if arcor returns success
	 * @throws Exception
	 *             if an Error occures
	 */
	private boolean afterSmsSent(final ConnectorContext ctx,
			final HttpResponse response) throws Exception {

		final String body = Utils.stream2str(response.getEntity().getContent());

		Log.d(TAG, "response: " + body);

		if (body == null || body.length() == 0) {
			throw new WebSMSException("No Response!");// TODO
		}

		if(body.indexOf(CHECK_SUCCESS) != -1){
			return true;
		}
		
		if(body.indexOf(CHECK_WRONGCAPTCHA) != -1){
			throw new WebSMSException(CHECK_WRONGCAPTCHA);
		}
		
		if(body.indexOf(CHECK_WRONG_NUMBER) != -1){
			throw new WebSMSException(CHECK_WRONG_NUMBER);
		}
		
		if(body.indexOf(CHECK_NO_GEOCELL) != -1 ) {
			throw new WebSMSException(CHECK_NO_GEOCELL);
		}

		throw new WebSMSException("SMS არ გაიგზავნა :( თუ ხშირად მეორდება მიმართე პროგრამისტს.");
	}


	/**
	 * {@inheritDoc}
	 * @throws IOException 
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent)
			throws WebSMSException, IOException {
		final ConnectorContext ctx = ConnectorContext.create(context, intent);
		if (this.login(ctx)) {
			String captcha = solveCaptcha(ctx);
			if(captcha != null){
				sendSms(ctx,captcha);
			}
		}

	}
	
	/**
	 * Load captcha and wait for user input to solve it.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param flow
	 *            _flowExecutionKey
	 * @return true if captcha was solved
	 * @throws IOException
	 *             IOException
	 */
	private String solveCaptcha(final ConnectorContext ctx)
			throws IOException {
		
		HttpGet cap = new HttpGet(URL_CAPTCHA);
		cap.addHeader("Referer", "http://www.sms.ge/ngeo/index.php");
		cap.setHeader("User-Agent", FAKE_USER_AGENT);
		HttpResponse response = ctx.getClient().execute(cap);
		int resp = response.getStatusLine().getStatusCode();
		if (resp != HttpURLConnection.HTTP_OK) {
			throw new WebSMSException(ctx.getContext(), R.string.error_http, "" + resp);
		}
		BitmapDrawable captcha = new BitmapDrawable(response.getEntity()
				.getContent());
		final Intent intent = new Intent(Connector.ACTION_CAPTCHA_REQUEST);
		intent.putExtra(Connector.EXTRA_CAPTCHA_DRAWABLE, captcha.getBitmap());
		captcha = null;
		Context context = ctx.getContext();
		this.getSpec(context).setToIntent(intent);
		context.sendBroadcast(intent);
		try {
			synchronized (CAPTCHA_SYNC) {
				CAPTCHA_SYNC.wait(CAPTCHA_TIMEOUT);
			}
		} catch (InterruptedException e) {
			Log.e(TAG, null, e);
			return null;
		}
		if (captchaSolve == null) {
			return captchaSolve;
		}
		// got user response, try to solve captcha
		Log.d(TAG, "got solved captcha: " + captchaSolve);
		
		return captchaSolve;
	}
	
	/**
	 * {@inheritDoc}
	 */
	protected final void gotSolvedCaptcha(final Context context,
			final String solvedCaptcha) {
		captchaSolve = solvedCaptcha;
		synchronized (CAPTCHA_SYNC) {
			CAPTCHA_SYNC.notify();
		}
	}

}
