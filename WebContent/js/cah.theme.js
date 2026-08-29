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
 * Dark mode. Deliberately has no dependencies (not even jQuery) and must be the first script
 * loaded in <head>, so a saved preference is applied before the rest of the page loads and
 * paints -- avoiding a flash of the wrong theme. With no saved preference, the OS/browser's
 * prefers-color-scheme is used instead (handled entirely in cah.css); this only needs to do
 * anything when the player has explicitly overridden that via the toggle button.
 */
(function() {
  var COOKIE_NAME = 'pyx_theme';

  function getSavedTheme() {
    var match = document.cookie.match(new RegExp('(?:^|; )' + COOKIE_NAME + '=([^;]*)'));
    return match ? decodeURIComponent(match[1]) : null;
  }

  function applyTheme(theme) {
    var classes = document.documentElement.classList;
    classes.remove('light-theme', 'dark-theme');
    if (theme === 'light' || theme === 'dark') {
      classes.add(theme + '-theme');
    }
  }

  function isCurrentlyDark() {
    var classes = document.documentElement.classList;
    if (classes.contains('dark-theme')) {
      return true;
    }
    if (classes.contains('light-theme')) {
      return false;
    }
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  window.pyxToggleTheme = function() {
    var next = isCurrentlyDark() ? 'light' : 'dark';
    var expires = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toUTCString();
    document.cookie = COOKIE_NAME + '=' + next + '; expires=' + expires + '; path=/';
    applyTheme(next);
  };

  applyTheme(getSavedTheme());
})();
