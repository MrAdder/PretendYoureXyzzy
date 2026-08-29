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

package net.socialgamer.cah.handlers;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import net.socialgamer.cah.CahModule.GlobalChatEnabled;
import net.socialgamer.cah.Constants.AjaxOperation;
import net.socialgamer.cah.Constants.AjaxRequest;
import net.socialgamer.cah.Constants.ErrorCode;
import net.socialgamer.cah.Constants.LongPollEvent;
import net.socialgamer.cah.Constants.LongPollResponse;
import net.socialgamer.cah.Constants.ReturnableData;
import net.socialgamer.cah.Constants.SessionAttribute;
import net.socialgamer.cah.RequestWrapper;
import net.socialgamer.cah.data.ConnectedUsers;
import net.socialgamer.cah.data.QueuedMessage.MessageType;
import net.socialgamer.cah.data.User;
import net.socialgamer.cah.util.ChatFilter;


/**
 * Handler for chat messages.
 *
 * @author Andy Janata (ajanata@socialgamer.net)
 */
public class ChatHandler extends Handler {

  private static final Logger LOG = LogManager.getLogger(ChatHandler.class);
  public static final String OP = AjaxOperation.CHAT.toString();

  private final ChatFilter chatFilter;
  private final ConnectedUsers users;
  private final boolean globalChatEnabled;

  @Inject
  public ChatHandler(final ConnectedUsers users,
      @GlobalChatEnabled final boolean globalChatEnabled, final ChatFilter chatFilter) {
    this.users = users;
    this.globalChatEnabled = globalChatEnabled;
    this.chatFilter = chatFilter;
  }

  @Override
  public Map<ReturnableData, Object> handle(final RequestWrapper request,
      final HttpSession session) {
    final User user = (User) session.getAttribute(SessionAttribute.USER);
    assert (user != null);
    final boolean wall = request.getParameter(AjaxRequest.WALL) != null
        && Boolean.valueOf(request.getParameter(AjaxRequest.WALL));
    final boolean emote = request.getParameter(AjaxRequest.EMOTE) != null
        && Boolean.valueOf(request.getParameter(AjaxRequest.EMOTE));

    if (request.getParameter(AjaxRequest.MESSAGE) == null) {
      return error(ErrorCode.NO_MSG_SPECIFIED);
    }
    if (wall && !user.isAdmin()) {
      return error(ErrorCode.NOT_ADMIN);
    }
    if (!globalChatEnabled && !user.isAdmin()) {
      // global chat can be turned off in the properties file
      return error(ErrorCode.NOT_ADMIN);
    }

    final String message = request.getParameter(AjaxRequest.MESSAGE).trim();
    final ChatFilter.Result filterResult = chatFilter.filterGlobal(user, message);
    final ErrorCode filterError = errorForFilterResult(filterResult);
    if (filterError != null) {
      return error(filterError);
    }

    broadcastMessage(user, message, wall, emote, filterResult);
    return new HashMap<ReturnableData, Object>();
  }

  /**
   * @return The error to return to the client for a chat filter result that should block the
   *         message, or {@code null} if the message should be broadcast.
   */
  @Nullable
  private ErrorCode errorForFilterResult(final ChatFilter.Result filterResult) {
    switch (filterResult) {
      case CAPSLOCK:
        return ErrorCode.CAPSLOCK;
      case NO_MESSAGE:
        return ErrorCode.NO_MSG_SPECIFIED;
      case NOT_ENOUGH_SPACES:
        return ErrorCode.NOT_ENOUGH_SPACES;
      case REPEAT:
        return ErrorCode.REPEAT_MESSAGE;
      case REPEAT_WORDS:
        return ErrorCode.REPEATED_WORDS;
      case TOO_FAST:
        return ErrorCode.TOO_FAST;
      case TOO_LONG:
        return ErrorCode.MESSAGE_TOO_LONG;
      case TOO_MANY_SPECIALS:
        return ErrorCode.TOO_MANY_SPECIAL_CHARACTERS;
      case DROP_MESSAGE:
      case OK:
      default:
        return null;
    }
  }

  private void broadcastMessage(final User user, final String message, final boolean wall,
      final boolean emote, final ChatFilter.Result filterResult) {
    LongPollEvent event = LongPollEvent.CHAT;
    if (filterResult == ChatFilter.Result.DROP_MESSAGE) {
      // Don't tell the user we dropped it, and don't send it to everyone else...
      // but let any online admins know about it
      event = LongPollEvent.FILTERED_CHAT;
    } else if (filterResult != ChatFilter.Result.OK) {
      LOG.error("Unknown chat filter result {}", filterResult);
    }

    final HashMap<ReturnableData, Object> broadcastData = new HashMap<ReturnableData, Object>();
    broadcastData.put(LongPollResponse.EVENT, event.toString());
    broadcastData.put(LongPollResponse.FROM, user.getNickname());
    broadcastData.put(LongPollResponse.MESSAGE, message);
    broadcastData.put(LongPollResponse.ID_CODE, user.getIdCode());
    broadcastData.put(LongPollResponse.SIGIL, user.getSigil().toString());
    if (user.isAdmin()) {
      broadcastData.put(LongPollResponse.FROM_ADMIN, true);
    }
    if (wall) {
      broadcastData.put(LongPollResponse.WALL, true);
    }
    if (emote) {
      broadcastData.put(LongPollResponse.EMOTE, true);
    }
    if (LongPollEvent.CHAT == event) {
      users.broadcastToAll(MessageType.CHAT, broadcastData);
    } else {
      users.broadcastToList(users.getAdmins(), MessageType.CHAT, broadcastData);
    }
  }
}
