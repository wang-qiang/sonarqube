/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.ws;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.RequestHandler;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.issue.IssueService;

import java.util.Collection;

/**
 * Set tags on an issue.
 * @since 5.1
 */
public class SetTagsAction implements RequestHandler {

  private static final Splitter WS_TAGS_SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

  private final IssueService service;

  public SetTagsAction(IssueService service) {
    this.service = service;
  }

  void define(WebService.NewController controller) {
    NewAction action = controller.createAction("set_tags")
      .setHandler(this)
      .setPost(true)
      .setSince("5.1")
      .setDescription("Set tags on an issue. Requires authentication and Browse permission on project");
    action.createParam("key")
      .setDescription("Issue key")
      .setExampleValue("5bccd6e8-f525-43a2-8d76-fcb13dde79ef")
      .setRequired(true);
    action.createParam("tags")
      .setDescription("A comma separated list of tags")
      .setExampleValue("security,cwe,misra-c")
      .setRequired(true)
      .setDefaultValue("");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String key = request.mandatoryParam("key");
    String tags = request.mandatoryParam("tags");
    Collection<String> resultTags = service.setTags(key, ImmutableSet.copyOf(WS_TAGS_SPLITTER.split(tags)));
    JsonWriter json = response.newJsonWriter().beginObject().name("tags").beginArray();
    for (String tag : resultTags) {
      json.value(tag);
    }
    json.endArray().endObject().close();
  }

}
