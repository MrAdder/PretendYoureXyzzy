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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Provider;

import net.socialgamer.cah.CahModule.Admins;
import net.socialgamer.cah.CahModule.BanList;
import net.socialgamer.cah.CahModule.BannedNicks;
import net.socialgamer.cah.CahModule.SessionPermalinkUrlFormat;
import net.socialgamer.cah.CahModule.ShowSessionPermalink;
import net.socialgamer.cah.CahModule.ShowUserPermalink;
import net.socialgamer.cah.CahModule.UserPermalinkUrlFormat;
import net.socialgamer.cah.CahModule.UserPersistentId;
import net.socialgamer.cah.Constants.AjaxOperation;
import net.socialgamer.cah.Constants.AjaxRequest;
import net.socialgamer.cah.Constants.AjaxResponse;
import net.socialgamer.cah.Constants.ErrorCode;
import net.socialgamer.cah.Constants.ReturnableData;
import net.socialgamer.cah.Constants.SessionAttribute;
import net.socialgamer.cah.RequestWrapper;
import net.socialgamer.cah.data.ConnectedUsers;
import net.socialgamer.cah.data.NewUserInfo;
import net.socialgamer.cah.data.User;
import net.socialgamer.cah.util.IdCodeMangler;
import net.socialgamer.cah.util.LogSanitizer;


/**
 * Handler to register a name with the server and get connected.
 *
 * @author Andy Janata (ajanata@socialgamer.net)
 */
public class RegisterHandler extends Handler {

  private static final Logger LOG = LogManager.getLogger(RegisterHandler.class);
  public static final String OP = AjaxOperation.REGISTER.toString();

  private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z_]\\w{2,29}");
  private static final int ID_CODE_MIN_LENGTH = 8;
  private static final int ID_CODE_MAX_LENGTH = 100;

  private final ConnectedUsers users;
  private final Set<String> adminList;
  private final Set<String> banList;
  private final Set<String> bannedNickList;
  private final User.Factory userFactory;
  private final Provider<String> persistentIdProvider;
  private final IdCodeMangler idCodeMangler;
  private final boolean showSessionPermalink;
  private final String sessionPermalinkFormatString;
  private final boolean showUserPermalink;
  private final String userPermalinkFormatString;

  @Inject
  public RegisterHandler(final ConnectedUsers users, @BanList final Set<String> banList,
      final User.Factory userFactory, final IdCodeMangler idCodeMangler,
      @UserPersistentId final Provider<String> persistentIdProvider,
      @Admins final Set<String> adminList,
      @ShowSessionPermalink final boolean showSessionPermalink,
      @SessionPermalinkUrlFormat final String sessionPermalinkFormatString,
      @ShowUserPermalink final boolean showUserPermalink,
      @UserPermalinkUrlFormat final String userPermalinkFormatString,
      @BannedNicks final Set<String> bannedNickList) {
    this.users = users;
    this.banList = banList;
    this.userFactory = userFactory;
    this.persistentIdProvider = persistentIdProvider;
    this.idCodeMangler = idCodeMangler;
    this.adminList = adminList;
    this.showSessionPermalink = showSessionPermalink;
    this.sessionPermalinkFormatString = sessionPermalinkFormatString;
    this.showUserPermalink = showUserPermalink;
    this.userPermalinkFormatString = userPermalinkFormatString;
    this.bannedNickList = bannedNickList;
  }

  @Override
  public Map<ReturnableData, Object> handle(final RequestWrapper request,
      final HttpSession session) {
    if (banList.contains(request.getRemoteAddr())) {
      LOG.info("Rejecting user {} from {} because they are banned.",
          LogSanitizer.sanitize(request.getParameter(AjaxRequest.NICKNAME)),
          request.getRemoteAddr());
      return error(ErrorCode.BANNED);
    }

    final ErrorCode validationError = validateRequest(request);
    if (validationError != null) {
      return error(validationError);
    }

    final String nick = request.getParameter(AjaxRequest.NICKNAME).trim();
    final User user = createUser(request, nick);
    user.userDidSomething();
    user.contactedServer();
    final ErrorCode errorCode = users.checkAndAdd(user);
    if (null != errorCode) {
      return error(errorCode);
    }

    return buildResponseData(session, user, nick);
  }

  /**
   * @return The error to return to the client if the request is invalid, or {@code null} if it's
   *         fine to proceed.
   */
  private ErrorCode validateRequest(final RequestWrapper request) {
    if (request.getParameter(AjaxRequest.NICKNAME) == null) {
      return ErrorCode.NO_NICK_SPECIFIED;
    }
    final String idCode = request.getParameter(AjaxRequest.ID_CODE);
    if (idCode != null && (idCode.trim().length() < ID_CODE_MIN_LENGTH
        || idCode.trim().length() > ID_CODE_MAX_LENGTH)) {
      return ErrorCode.INVALID_ID_CODE;
    }

    final String nick = request.getParameter(AjaxRequest.NICKNAME).trim();
    final String nickLower = nick.toLowerCase(Locale.ENGLISH);
    for (final String banned : bannedNickList) {
      if (nickLower.contains(banned)) {
        return ErrorCode.RESERVED_NICK;
      }
    }
    if (!VALID_NAME.matcher(nick).matches()) {
      return ErrorCode.INVALID_NICK;
    }
    return null;
  }

  private User createUser(final RequestWrapper request, final String nick) {
    String persistentId = request.getParameter(AjaxRequest.PERSISTENT_ID);
    if (StringUtils.isBlank(persistentId)) {
      persistentId = persistentIdProvider.get();
    }
    final String mangledIdCode = idCodeMangler.mangle(nick,
        request.getParameter(AjaxRequest.ID_CODE));
    final NewUserInfo info = new NewUserInfo(nick, mangledIdCode, request.getRemoteAddr(),
        adminList.contains(request.getRemoteAddr()), persistentId,
        request.getHeader(HttpHeaders.ACCEPT_LANGUAGE), request.getHeader(HttpHeaders.USER_AGENT));
    return userFactory.create(info);
  }

  // There is a findbugs warning on the session.setAttribute call below: Store of non serializable
  // net.socialgamer.cah.data.User into HttpSession in
  // net.socialgamer.cah.handlers.RegisterHandler.handle(RequestWrapper, HttpSession)
  // I am choosing to ignore this for the time being as it works under light load, and I am
  // intending on moving away from HttpSession for storing user data in the not too distant
  // future, to fix issues with loading the game twice in the same browser. Making User (and
  // transitively Game, GameManager, etc.) actually serializable would be a much larger change for
  // no benefit, since sessions are never clustered or persisted in this deployment model.
  @SuppressWarnings("java:S2441")
  private Map<ReturnableData, Object> buildResponseData(final HttpSession session, final User user,
      final String nick) {
    session.setAttribute(SessionAttribute.USER, user);

    final Map<ReturnableData, Object> data = new HashMap<ReturnableData, Object>();
    data.put(AjaxResponse.NICKNAME, nick);
    data.put(AjaxResponse.PERSISTENT_ID, user.getPersistentId());
    data.put(AjaxResponse.ID_CODE, user.getIdCode());
    data.put(AjaxResponse.SIGIL, user.getSigil().toString());
    if (showSessionPermalink) {
      data.put(AjaxResponse.SESSION_PERMALINK,
          String.format(sessionPermalinkFormatString, user.getSessionId()));
    }
    if (showUserPermalink) {
      data.put(AjaxResponse.USER_PERMALINK,
          String.format(userPermalinkFormatString, user.getPersistentId()));
    }
    return data;
  }
}
