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

package net.socialgamer.cah.data;

import javax.annotation.Nullable;

/**
 * Bundles the client-supplied properties needed to create a new {@link User}, so its constructor
 * and Guice assisted-inject {@link User.Factory} don't need a long list of individual parameters.
 */
public final class NewUserInfo {
  public final String nickname;
  public final String idCode;
  public final String hostname;
  public final boolean isAdmin;
  public final String persistentId;
  @Nullable
  public final String clientLanguage;
  @Nullable
  public final String clientAgent;

  public NewUserInfo(final String nickname, final String idCode, final String hostname,
      final boolean isAdmin, final String persistentId, @Nullable final String clientLanguage,
      @Nullable final String clientAgent) {
    this.nickname = nickname;
    this.idCode = idCode;
    this.hostname = hostname;
    this.isAdmin = isAdmin;
    this.persistentId = persistentId;
    this.clientLanguage = clientLanguage;
    this.clientAgent = clientAgent;
  }
}
