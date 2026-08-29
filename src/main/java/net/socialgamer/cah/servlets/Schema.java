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
import java.util.EnumSet;
import java.util.Map;

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
import org.hibernate.tool.schema.SourceType;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.DefaultSchemaFilter;
import org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToWriter;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.ScriptSourceInput;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.SourceDescriptor;
import org.hibernate.tool.schema.spi.TargetDescriptor;


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
    // Force PostgreSQL regardless of what's actually configured (likely SQLiteDialect for local
    // dev): this endpoint exists to show what the schema would look like for a production deploy.
    final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
        .configure()
        .applySetting(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName())
        .build();
    try {
      final Metadata metadata = new MetadataSources(registry).buildMetadata();
      final PrintWriter out;
      try {
        out = response.getWriter();
      } catch (final IOException e) {
        LOG.error("Unable to get response writer for schema export", e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Unable to get response writer.");
        return;
      }
      final ScriptTargetOutput target = new ScriptTargetOutputToWriter(out);
      registry.getService(SchemaManagementTool.class)
          .getSchemaCreator(Map.of())
          .doCreation(metadata, EXECUTION_OPTIONS, ContributableMatcher.ALL, SOURCE_DESCRIPTOR,
              new TargetDescriptor() {
                @Override
                public EnumSet<TargetType> getTargetTypes() {
                  return EnumSet.of(TargetType.SCRIPT);
                }

                @Override
                public ScriptTargetOutput getScriptTargetOutput() {
                  return target;
                }
              });
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  private static final ExecutionOptions EXECUTION_OPTIONS = new ExecutionOptions() {
    @Override
    public Map<String, Object> getConfigurationValues() {
      return Map.of();
    }

    @Override
    public boolean shouldManageNamespaces() {
      return true;
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
      return ExceptionHandlerLoggedImpl.INSTANCE;
    }

    @Override
    public SchemaFilter getSchemaFilter() {
      return DefaultSchemaFilter.INSTANCE;
    }
  };

  private static final SourceDescriptor SOURCE_DESCRIPTOR = new SourceDescriptor() {
    @Override
    public SourceType getSourceType() {
      return SourceType.METADATA;
    }

    @Override
    public ScriptSourceInput getScriptSourceInput() {
      return null;
    }
  };
}
