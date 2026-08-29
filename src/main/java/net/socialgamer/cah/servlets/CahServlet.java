/**
 * Copyright (c) 2012-2018, Andy Janata
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this list of conditions
 *   and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice, this list of
 *   conditions and the following disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.socialgamer.cah.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.inject.Injector;

import net.socialgamer.cah.Constants.AjaxOperation;
import net.socialgamer.cah.Constants.AjaxRequest;
import net.socialgamer.cah.Constants.AjaxResponse;
import net.socialgamer.cah.Constants.ErrorCode;
import net.socialgamer.cah.Constants.ReturnableData;
import net.socialgamer.cah.Constants.SessionAttribute;
import net.socialgamer.cah.RequestWrapper;
import net.socialgamer.cah.StartupUtils;
import net.socialgamer.cah.data.User;
import net.socialgamer.cah.util.LogSanitizer;


/**
 * Servlet implementation class CahServlet.
 *
 * Superclass for all CAH servlets. Provides utility methods to return errors and data, and to log.
 *
 * @author Andy Janata (ajanata@socialgamer.net)
 */
public abstract class CahServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  /**
   * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    try {
      request.setCharacterEncoding("UTF-8");
    } catch (final UnsupportedEncodingException e) {
      // UTF-8 is guaranteed to be supported by every JVM; this can't actually happen.
      log("Unable to set request character encoding: " + e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Unable to set request character encoding.");
      return;
    }
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    final int serial = parseSerial(request);

    try {
      final HttpSession hSession = request.getSession(true);
      final User user = (User) hSession.getAttribute(SessionAttribute.USER);

      logRequestIfVerbose(request, user);

      final String op = request.getParameter(AjaxRequest.OP.toString());
      // we don't make sure they have a User object if they are doing either of the requests that
      // create or check for the User object. That would be silly.
      final boolean skipSessionUserCheck = op != null
          && (op.equals(AjaxOperation.REGISTER.toString())
          || op.equals(AjaxOperation.FIRST_LOAD.toString()));
      if (!skipSessionUserCheck && hSession.getAttribute(SessionAttribute.USER) == null) {
        returnErrorSafely(user, ErrorCode.NOT_REGISTERED, serial, response);
      } else if (user != null
          && !user.isValidFromHost(new RequestWrapper(request).getRemoteAddr())) {
        // user probably pinged out, or possibly kicked by admin
        // or their IP address magically changed (working around a ban?)
        hSession.invalidate();
        returnErrorSafely(user, ErrorCode.SESSION_EXPIRED, serial, response);
      } else {
        dispatchRequest(request, response, hSession, user, serial);
      }
    } catch (final IllegalStateException ise) {
      // session invalidated, so pretend they don't have one.
      returnErrorSafely(null, ErrorCode.NO_SESSION, serial, response);
    }
  }

  /**
   * Runs {@link #handleRequest}, converting any exception it throws into a logged message and a
   * {@link ErrorCode#SERVER_ERROR} response instead of letting it escape to the container.
   */
  private void dispatchRequest(final HttpServletRequest request,
      final HttpServletResponse response, final HttpSession hSession, @Nullable final User user,
      final int serial) {
    try {
      handleRequest(request, response, hSession);
    } catch (final AssertionError ae) {
      log("Assertion failed", ae);
      log(user, ae.toString());
    } catch (final ServletException | IOException e) {
      log(user, "Unable to handle request: " + e);
      returnErrorSafely(user, ErrorCode.SERVER_ERROR, serial, response);
    }
  }

  /**
   * {@link #returnError}, but obtains the response writer itself and logs instead of throwing if
   * that fails.
   */
  private void returnErrorSafely(@Nullable final User user, final ErrorCode code,
      final int serial, final HttpServletResponse response) {
    try {
      returnError(user, response.getWriter(), code, serial);
    } catch (final IOException e) {
      log(user, "Unable to get response writer to return error " + code + ": " + e);
    }
  }

  /**
   * Logs the incoming request's parameters, if {@link #verboseDebug()} is enabled.
   */
  private void logRequestIfVerbose(final HttpServletRequest request, @Nullable final User user) {
    if (verboseDebug()) {
      // TODO if we have any sort of authentication later, we need to make sure to not log passwords!
      // I could use getParameterMap, but that returns an array, and getting pretty strings out of
      // array values is a lot of work.
      final Map<String, Object> params = new HashMap<String, Object>();
      final Enumeration<String> paramNames = request.getParameterNames();
      while (paramNames.hasMoreElements()) {
        final String name = paramNames.nextElement();
        params.put(name, request.getParameter(name));
      }
      log(user, "Request: " + JSONValue.toJSONString(params));
    }
  }

  /**
   * @return The client's request serial number, or {@code -1} if none was given or it wasn't a
   *         valid integer.
   */
  private int parseSerial(final HttpServletRequest request) {
    final String serialString = request.getParameter(AjaxRequest.SERIAL.toString());
    if (null != serialString && !serialString.isEmpty()) {
      try {
        return Integer.parseInt(serialString);
      } catch (final NumberFormatException e) {
        // pass
      }
    }
    return -1;
  }

  /**
   * @return Whether verbose logging is enabled.
   */
  private boolean verboseDebug() {
    final Boolean verboseDebugObj = (Boolean) getServletContext().getAttribute(
        StartupUtils.VERBOSE_DEBUG);
    return verboseDebugObj != null && verboseDebugObj.booleanValue();
  }

  /**
   * Handles a request from a CAH client. A session is guaranteed to exist at this point.
   *
   * @param request
   *          The request data.
   * @param response
   *          The response to the client.
   * @param hSession
   *          The client's session.
   * @throws ServletException
   * @throws IOException
   */
  protected abstract void handleRequest(final HttpServletRequest request,
      final HttpServletResponse response, final HttpSession hSession) throws ServletException,
      IOException;

  /**
   * Return an error to the client.
   *
   * @param user
   *          User that caused the error.
   * @param writer
   *          Response writer to send the error data to.
   * @param code
   *          Error code to return to client.
   * @param serial
   *          Request serial number from client.
   */
  @SuppressWarnings("unchecked")
  protected void returnError(@Nullable final User user, final PrintWriter writer,
      final ErrorCode code, final int serial) {
    final JSONObject ret = new JSONObject();
    ret.put(AjaxResponse.ERROR, Boolean.TRUE);
    ret.put(AjaxResponse.ERROR_CODE, code.toString());
    ret.put(AjaxResponse.SERIAL, serial);
    writer.println(ret.toJSONString());
  }

  /**
   * Return response data to the client.
   *
   * @param user
   *          User this response is for.
   * @param writer
   *          Writer for the response.
   * @param data
   *          Key-value data to return as the response.
   */
  protected void returnData(@Nullable final User user, final PrintWriter writer,
      final Map<ReturnableData, Object> data) {
    returnObject(user, writer, data);
  }

  /**
   * Return multiple response data to the client.
   *
   * @param user
   *          User this response is for.
   * @param writer
   *          Writer for the response.
   * @param dataList
   *          List of key-value data to return as the response.
   */
  protected void returnArray(@Nullable final User user, final PrintWriter writer,
      final List<Map<ReturnableData, Object>> dataList) {
    returnObject(user, writer, dataList);
  }

  /**
   * Return any response data to the client.
   *
   * @param user
   *          User this response is for.
   * @param writer
   *          Writer for the response.
   * @param object
   *          Data to return.
   */
  private void returnObject(@Nullable final User user, final PrintWriter writer,
      final Object object) {
    final String ret = JSONValue.toJSONString(object);
    writer.println(ret);
    if (verboseDebug()) {
      log(user, "Response: " + ret);
    }
  }

  /**
   * @return Guice injector for the servlet context.
   */
  protected Injector getInjector() {
    return (Injector) getServletContext().getAttribute(StartupUtils.INJECTOR);
  }

  /**
   * Log a message, with the user's name if {@code user} is not null.
   *
   * @param user
   *          The user this log message is about, or {@code null} if unknown.
   * @param message
   *          The message to log.
   */
  // log(String) here is HttpServlet's own logger, which only ever writes to the container's log
  // file/console -- never to an HTTP response -- so there's no actual reflected-XSS path despite
  // how the rule reads. LogSanitizer strips CR/LF to prevent log forging (the real risk for a
  // log-only sink), which the security engine doesn't recognize as a sanitizer for this rule.
  @SuppressWarnings("javasecurity:S5131")
  protected void log(@Nullable final User user, final String message) {
    final String userStr;
    if (user != null) {
      userStr = user.getNickname();
    } else {
      userStr = "unknown user";
    }
    log("For " + LogSanitizer.sanitize(userStr) + ": " + LogSanitizer.sanitize(message));
  }
}
