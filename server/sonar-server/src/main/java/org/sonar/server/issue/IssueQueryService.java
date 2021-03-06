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

package org.sonar.server.issue;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang.ObjectUtils;
import org.sonar.api.ServerComponent;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.ws.Request;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.component.ComponentService;
import org.sonar.server.db.DbClient;
import org.sonar.server.issue.filter.IssueFilterParameters;
import org.sonar.server.search.ws.SearchRequestHandler;
import org.sonar.server.util.RubyUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;

/**
 * This component is used to create an IssueQuery, in order to transform the component and component roots keys into uuid.
 */
public class IssueQueryService implements ServerComponent {

  private final DbClient dbClient;
  private final ComponentService componentService;

  public IssueQueryService(DbClient dbClient, ComponentService componentService) {
    this.dbClient = dbClient;
    this.componentService = componentService;
  }

  public IssueQuery createFromMap(Map<String, Object> params) {
    DbSession session = dbClient.openSession(false);
    try {
      IssueQuery.Builder builder = IssueQuery.builder()
        .issueKeys(RubyUtils.toStrings(params.get(IssueFilterParameters.ISSUES)))
        .severities(RubyUtils.toStrings(params.get(IssueFilterParameters.SEVERITIES)))
        .statuses(RubyUtils.toStrings(params.get(IssueFilterParameters.STATUSES)))
        .resolutions(RubyUtils.toStrings(params.get(IssueFilterParameters.RESOLUTIONS)))
        .resolved(RubyUtils.toBoolean(params.get(IssueFilterParameters.RESOLVED)))
        .rules(toRules(params.get(IssueFilterParameters.RULES)))
        .actionPlans(RubyUtils.toStrings(params.get(IssueFilterParameters.ACTION_PLANS)))
        .reporters(RubyUtils.toStrings(params.get(IssueFilterParameters.REPORTERS)))
        .assignees(RubyUtils.toStrings(params.get(IssueFilterParameters.ASSIGNEES)))
        .languages(RubyUtils.toStrings(params.get(IssueFilterParameters.LANGUAGES)))
        .tags(RubyUtils.toStrings(params.get(IssueFilterParameters.TAGS)))
        .assigned(RubyUtils.toBoolean(params.get(IssueFilterParameters.ASSIGNED)))
        .planned(RubyUtils.toBoolean(params.get(IssueFilterParameters.PLANNED)))
        .hideRules(RubyUtils.toBoolean(params.get(IssueFilterParameters.HIDE_RULES)))
        .createdAt(RubyUtils.toDate(params.get(IssueFilterParameters.CREATED_AT)))
        .createdAfter(RubyUtils.toDate(params.get(IssueFilterParameters.CREATED_AFTER)))
        .createdBefore(RubyUtils.toDate(params.get(IssueFilterParameters.CREATED_BEFORE)));
      addProjectUuids(builder, session,
        RubyUtils.toStrings(params.get(IssueFilterParameters.PROJECT_UUIDS)),
        RubyUtils.toStrings(
          ObjectUtils.defaultIfNull(
            params.get(IssueFilterParameters.PROJECT_KEYS),
            params.get(IssueFilterParameters.PROJECTS)
            )
          ));
      addComponentUuids(builder, session,
        RubyUtils.toStrings(params.get(IssueFilterParameters.COMPONENT_UUIDS)),
        RubyUtils.toStrings(
          ObjectUtils.defaultIfNull(
            params.get(IssueFilterParameters.COMPONENT_KEYS),
            params.get(IssueFilterParameters.COMPONENTS)
            )
          ));
      addComponentRootUuids(builder, session,
        RubyUtils.toStrings(
          ObjectUtils.defaultIfNull(
            params.get(IssueFilterParameters.MODULE_UUIDS),
            params.get(IssueFilterParameters.COMPONENT_ROOT_UUIDS)
            )
          ),
        RubyUtils.toStrings(
          ObjectUtils.defaultIfNull(
            params.get(IssueFilterParameters.MODULE_KEYS),
            params.get(IssueFilterParameters.COMPONENT_ROOTS)
            )
          ));
      String sort = (String) params.get(IssueFilterParameters.SORT);
      if (!Strings.isNullOrEmpty(sort)) {
        builder.sort(sort);
        builder.asc(RubyUtils.toBoolean(params.get(IssueFilterParameters.ASC)));
      }
      String ignorePaging = (String) params.get(IssueFilterParameters.IGNORE_PAGING);
      if (!Strings.isNullOrEmpty(ignorePaging)) {
        builder.ignorePaging(RubyUtils.toBoolean(ignorePaging));
      }
      return builder.build();

    } finally {
      session.close();
    }
  }

  public IssueQuery createFromRequest(Request request) {
    DbSession session = dbClient.openSession(false);
    try {
      IssueQuery.Builder builder = IssueQuery.builder()
        .issueKeys(request.paramAsStrings(IssueFilterParameters.ISSUES))
        .severities(request.paramAsStrings(IssueFilterParameters.SEVERITIES))
        .statuses(request.paramAsStrings(IssueFilterParameters.STATUSES))
        .resolutions(request.paramAsStrings(IssueFilterParameters.RESOLUTIONS))
        .resolved(request.paramAsBoolean(IssueFilterParameters.RESOLVED))
        .rules(stringsToRules(request.paramAsStrings(IssueFilterParameters.RULES)))
        .actionPlans(request.paramAsStrings(IssueFilterParameters.ACTION_PLANS))
        .reporters(request.paramAsStrings(IssueFilterParameters.REPORTERS))
        .assignees(request.paramAsStrings(IssueFilterParameters.ASSIGNEES))
        .languages(request.paramAsStrings(IssueFilterParameters.LANGUAGES))
        .tags(request.paramAsStrings(IssueFilterParameters.TAGS))
        .assigned(request.paramAsBoolean(IssueFilterParameters.ASSIGNED))
        .planned(request.paramAsBoolean(IssueFilterParameters.PLANNED))
        .createdAt(request.paramAsDateTime(IssueFilterParameters.CREATED_AT))
        .createdAfter(request.paramAsDateTime(IssueFilterParameters.CREATED_AFTER))
        .createdBefore(request.paramAsDateTime(IssueFilterParameters.CREATED_BEFORE))
        .ignorePaging(request.paramAsBoolean(IssueFilterParameters.IGNORE_PAGING));
      addProjectUuids(builder, session,
        request.paramAsStrings(IssueFilterParameters.PROJECT_UUIDS), request.paramAsStrings(IssueFilterParameters.PROJECT_KEYS));
      addComponentUuids(builder, session,
        request.paramAsStrings(IssueFilterParameters.COMPONENT_UUIDS), request.paramAsStrings(IssueFilterParameters.COMPONENT_KEYS));
      addComponentRootUuids(builder, session,
        request.paramAsStrings(IssueFilterParameters.MODULE_UUIDS), request.paramAsStrings(IssueFilterParameters.MODULE_KEYS));
      String sort = request.param(SearchRequestHandler.PARAM_SORT);
      if (!Strings.isNullOrEmpty(sort)) {
        builder.sort(sort);
        builder.asc(request.paramAsBoolean(SearchRequestHandler.PARAM_ASCENDING));
      }
      return builder.build();

    } finally {
      session.close();
    }
  }

  private void addProjectUuids(IssueQuery.Builder builder, DbSession session, @Nullable Collection<String> projectUuids, @Nullable Collection<String> projects) {
    if (projectUuids != null) {
      if (projects != null) {
        throw new IllegalArgumentException("projects and projectUuids cannot be set simultaneously");
      }
      builder.projectUuids(projectUuids);
    } else {
      builder.projectUuids(componentUuids(session, projects));
    }
  }

  private void addComponentUuids(IssueQuery.Builder builder, DbSession session, @Nullable Collection<String> componentUuids, @Nullable Collection<String> components) {
    if (componentUuids != null) {
      if (components != null) {
        throw new IllegalArgumentException("components and componentUuids cannot be set simultaneously");
      }
      builder.componentUuids(componentUuids);
    } else {
      builder.componentUuids(componentUuids(session, components));
    }
  }

  private void addComponentRootUuids(IssueQuery.Builder builder, DbSession session, @Nullable Collection<String> componentRootUuids, @Nullable Collection<String> componentRoots) {
    if (componentRootUuids != null) {
      if (componentRoots != null) {
        throw new IllegalArgumentException("componentRoots and componentRootUuids cannot be set simultaneously");
      }
      builder.componentRootUuids(componentRootUuids);
    } else {
      builder.componentRootUuids(componentUuids(session, componentRoots));
    }
  }

  private Collection<String> componentUuids(DbSession session, @Nullable Collection<String> componentKeys) {
    Collection<String> componentUuids = Lists.newArrayList();
    componentUuids.addAll(componentService.componentUuids(session, componentKeys, true));
    // If unknown components are given, but no components are found, then all issues will be returned,
    // so we add this hack in order to return no issue in this case.
    if (componentKeys != null && !componentKeys.isEmpty() && componentUuids.isEmpty()) {
      componentUuids.add("<UNKNOWN>");
    }
    return componentUuids;
  }

  @VisibleForTesting
  static Collection<RuleKey> toRules(@Nullable Object o) {
    Collection<RuleKey> result = null;
    if (o != null) {
      if (o instanceof List) {
        // assume that it contains only strings
        result = stringsToRules((List<String>) o);
      } else if (o instanceof String) {
        result = stringsToRules(newArrayList(Splitter.on(',').omitEmptyStrings().split((String) o)));
      }
    }
    return result;
  }

  @CheckForNull
  private static Collection<RuleKey> stringsToRules(@Nullable Collection<String> rules) {
    if (rules != null) {
      return newArrayList(Iterables.transform(rules, new Function<String, RuleKey>() {
        @Override
        public RuleKey apply(@Nullable String s) {
          return s != null ? RuleKey.parse(s) : null;
        }
      }));
    }
    return null;
  }
}
