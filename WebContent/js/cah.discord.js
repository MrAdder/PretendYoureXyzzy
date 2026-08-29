/**
 * Copyright (c) 2026, Andy Janata
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

/**
 * Bootstraps this page as a Discord Activity (embedded in a voice channel) when applicable.
 * A no-op for every normal browser visit: Discord Activities are loaded in an iframe and Discord
 * appends a frame_id query parameter to the launch URL, so both are required before this does
 * anything. Included on both index.jsp and game.jsp -- since Discord always launches an Activity at
 * the root URL, if we land here as an Activity from index.jsp we bounce straight into game.jsp
 * (preserving Discord's query params, which its SDK needs to read), which is where the actual SDK
 * gets initialized. Everything is wrapped so a failure here (e.g. we guessed wrong about being in
 * Discord) can never break the page for a normal visit.
 */
(function() {
  function isDiscordActivity() {
    var inIframe;
    try {
      inIframe = window.top !== window.self;
    } catch (e) {
      inIframe = true;
    }
    return inIframe && /[?&]frame_id=/.test(window.location.search);
  }

  if (!isDiscordActivity()) {
    return;
  }

  var onGamePage = /(^|\/)game\.jsp$/.test(window.location.pathname);
  if (!onGamePage) {
    window.location.replace('game.jsp' + window.location.search);
    return;
  }

  if (typeof cah === 'undefined' || !cah.DISCORD_CLIENT_ID) {
    return;
  }

  import('https://cdn.jsdelivr.net/npm/@discord/embedded-app-sdk/+esm')
      .then(function(module) {
        var discordSdk = new module.DiscordSDK(cah.DISCORD_CLIENT_ID);
        return discordSdk.ready();
      })
      .then(function() {
        console.log('Discord Activity SDK ready.');
      })
      .catch(function(e) {
        console.error('Unable to initialize Discord Activity SDK', e);
      });
})();
