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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.socialgamer.cah.CahModule.DiscordWebhookUrl;

/**
 * Posts short status messages to a Discord webhook, if one is configured.
 */
@Singleton
public class DiscordNotifier {
  private static final Logger LOG = LogManager.getLogger(DiscordNotifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final String webhookUrl;
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(TIMEOUT)
      .build();

  @Inject
  public DiscordNotifier(@DiscordWebhookUrl final String webhookUrl) {
    this.webhookUrl = webhookUrl;
  }

  /**
   * Post {@code message} to the configured webhook. A no-op if none is configured. Sends
   * asynchronously and never throws; failures are logged and otherwise ignored, since a
   * notification failure should never affect server startup/shutdown.
   */
  public void notify(final String message) {
    if (StringUtils.isBlank(webhookUrl)) {
      return;
    }

    final JSONObject payload = new JSONObject();
    payload.put("content", message);

    final HttpRequest request;
    try {
      request = HttpRequest.newBuilder()
          .uri(URI.create(webhookUrl))
          .timeout(TIMEOUT)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
          .build();
    } catch (final IllegalArgumentException e) {
      LOG.error("Invalid Discord webhook URL", e);
      return;
    }

    httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        .whenComplete((response, throwable) -> {
          if (throwable != null) {
            LOG.error("Unable to send Discord webhook notification", throwable);
          } else if (response.statusCode() >= 300) {
            LOG.error("Discord webhook returned HTTP {}", response.statusCode());
          }
        });
  }
}
