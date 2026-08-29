/**
 * Copyright (c) 2012, Andy Janata
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;


/**
 * Servlet implementation class Schema.
 *
 * Get the database schema for known Hibernate objects.
 */
@WebServlet("/Schema")
public class Schema extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger LOG = LogManager.getLogger(Schema.class);

  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    // Force PostgreSQL regardless of what's actually configured (likely SqliteDialect for local
    // dev): this endpoint exists to show what the schema would look like for a production deploy.
    final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .configure()
        .applySetting(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName())
        .build();
    try {
      final Metadata metadata = new MetadataSources(registry).buildMetadata();
      final Path tempFile;
      try {
        tempFile = createRestrictedTempFile();
      } catch (final IOException e) {
        LOG.error("Unable to create temporary file for schema export", e);
        try {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              "Unable to create temporary file for schema export.");
        } catch (final IOException e2) {
          LOG.error("Unable to send error response", e2);
        }
        return;
      }
      try {
        new SchemaExport()
            .setOutputFile(tempFile.toAbsolutePath().toString())
            .setDelimiter(";")
            .create(EnumSet.of(TargetType.SCRIPT), metadata);
        final PrintWriter out = response.getWriter();
        try {
          for (final String line : Files.readAllLines(tempFile)) {
            out.println(line);
          }
        } catch (final IOException e) {
          LOG.error("Unable to read back generated schema file {}", tempFile, e);
          out.println("-- Error reading generated schema; see server log.");
        }
      } finally {
        try {
          Files.deleteIfExists(tempFile);
        } catch (final IOException e) {
          LOG.warn("Unable to delete temporary schema file {}", tempFile, e);
        }
      }
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  /**
   * Create a temporary file readable and writable only by this process' own user.
   * java.io.tmpdir is typically shared/world-writable; restricting access at creation (rather
   * than via a chmod-style call afterward) avoids a window where the file briefly has default,
   * more permissive access. This requires a POSIX filesystem, which is what every supported
   * deployment target (Docker/Linux) uses.
   */
  private static Path createRestrictedTempFile() throws IOException {
    final FileAttribute<?> perms =
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    try {
      return Files.createTempFile("pyx-schema", ".sql", perms);
    } catch (final UnsupportedOperationException e) {
      throw new IOException(
          "This filesystem does not support POSIX file permissions; refusing to create a "
              + "temporary file without being able to restrict its permissions.", e);
    }
  }
}
