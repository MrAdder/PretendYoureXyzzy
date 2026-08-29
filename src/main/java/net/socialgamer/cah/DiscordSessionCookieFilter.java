/*
 * The author disclaims copyright to this source code. In place of
 * a legal notice, here is a blessing:
 *
 * May you do good and not evil.
 * May you find forgiveness for yourself and forgive others.
 * May you share freely, never taking more than you give.
 *
 */

package net.socialgamer.cah;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Response;

/**
 * Running as a Discord Activity requires the session cookie to carry {@code SameSite=None} and
 * {@code Partitioned}, or the browser silently refuses to store/send it inside Discord's iframe
 * (see https://docs.discord.com/developers/activities/development-guides/networking). Neither
 * attribute is expressible through the standard {@link javax.servlet.http.Cookie} API, and worse,
 * Jetty's session handling doesn't even go through that API on the response object the servlet
 * filter chain sees -- it writes the Set-Cookie header directly onto its own internal
 * {@link Response}, independent of any {@code HttpServletResponseWrapper} a filter installs. So
 * this reaches past the servlet API entirely: after the rest of the chain runs (by which point a
 * newly-created session's cookie, if any, has already been written), it finds that header on
 * Jetty's own {@link Response} and rewrites it in place.
 *
 * Only applied when the original client connection was HTTPS (checked via X-Forwarded-Proto,
 * since NPM/whatever reverse proxy terminates TLS and forwards plain HTTP to this container
 * internally -- the same trust model {@link RequestWrapper} already uses for X-Forwarded-For).
 * SameSite=None cookies are rejected outright by browsers unless also marked Secure, so this is
 * left as a no-op over plain HTTP -- meaning local dev on http://localhost is unaffected.
 *
 * Assumes the session cookie (named JSESSIONID, per this app's web.xml) is the only cookie this
 * app ever sets; if that changes, this will need to distinguish it from any others by name.
 */
public class DiscordSessionCookieFilter implements Filter {

  private static final Logger LOG = LogManager.getLogger(DiscordSessionCookieFilter.class);

  @Override
  public void doFilter(final ServletRequest request, final ServletResponse response,
      final FilterChain chain) throws IOException, ServletException {
    chain.doFilter(request, response);

    if (!isSecure((HttpServletRequest) request) || !(response instanceof Response)) {
      return;
    }

    final Response jettyResponse = (Response) response;
    if (jettyResponse.isCommitted()) {
      LOG.warn("Response already committed; can't rewrite session cookie for Discord");
      return;
    }

    final String setCookie = jettyResponse.getHttpFields().get(HttpHeader.SET_COOKIE);
    if (setCookie != null) {
      jettyResponse.getHttpFields().remove(HttpHeader.SET_COOKIE);
      jettyResponse.getHttpFields().add(HttpHeader.SET_COOKIE,
          setCookie + "; Secure; SameSite=None; Partitioned");
    }
  }

  private static boolean isSecure(final HttpServletRequest request) {
    final String forwardedProto = request.getHeader("X-Forwarded-Proto");
    if (forwardedProto != null) {
      return "https".equalsIgnoreCase(forwardedProto);
    }
    return request.isSecure();
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    // pass
  }

  @Override
  public void destroy() {
    // pass
  }
}
