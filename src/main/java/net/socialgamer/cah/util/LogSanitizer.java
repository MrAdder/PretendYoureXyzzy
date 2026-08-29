/*
 * The author disclaims copyright to this source code. In place of
 * a legal notice, here is a blessing:
 *
 * May you do good and not evil.
 * May you find forgiveness for yourself and forgive others.
 * May you share freely, never taking more than you give.
 *
 */

package net.socialgamer.cah.util;

/**
 * Strips characters from untrusted values (nicknames, chat text, custom deck URLs, etc.) that
 * could be used to forge additional log entries before those values are written to the log.
 */
public final class LogSanitizer {
  private LogSanitizer() {
  }

  public static String sanitize(final String value) {
    if (value == null) {
      return null;
    }
    return value.replaceAll("[\r\n\t]", "_");
  }
}
