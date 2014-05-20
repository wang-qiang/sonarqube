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

package org.sonar.server.component.ws;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.io.Resources;
import org.sonar.api.component.Component;
import org.sonar.api.i18n.I18n;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.RequestHandler;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.Durations;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.web.UserRole;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.measure.db.MeasureDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.core.properties.PropertiesDao;
import org.sonar.core.properties.PropertyDto;
import org.sonar.core.properties.PropertyQuery;
import org.sonar.core.resource.ResourceDao;
import org.sonar.core.resource.SnapshotDto;
import org.sonar.core.timemachine.Periods;
import org.sonar.server.db.DbClient;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.issue.IssueService;
import org.sonar.server.issue.RulesAggregation;
import org.sonar.server.measure.persistence.MeasureDao;
import org.sonar.server.user.UserSession;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.util.Date;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class ComponentAppAction implements RequestHandler {

  private static final String KEY = "key";

  private final DbClient dbClient;

  private final ResourceDao resourceDao;
  private final PropertiesDao propertiesDao;
  private final MeasureDao measureDao;
  private final IssueService issueService;
  private final Periods periods;
  private final Durations durations;
  private final I18n i18n;

  public ComponentAppAction(DbClient dbClient, IssueService issueService, Periods periods, Durations durations, I18n i18n) {
    this.dbClient = dbClient;
    this.resourceDao = dbClient.getDao(ResourceDao.class);
    this.propertiesDao = dbClient.getDao(PropertiesDao.class);
    this.measureDao = dbClient.getDao(MeasureDao.class);
    this.issueService = issueService;
    this.periods = periods;
    this.durations = durations;
    this.i18n = i18n;
  }

  void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("app")
      .setDescription("Coverage data required for rendering the component viewer")
      .setSince("4.4")
      .setInternal(true)
      .setHandler(this)
      .setResponseExample(Resources.getResource(this.getClass(), "components-app-example-show.json"));

    action
      .createParam(KEY)
      .setRequired(true)
      .setDescription("File key")
      .setExampleValue("org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/Plugin.java");
  }

  @Override
  public void handle(Request request, Response response) {
    String fileKey = request.mandatoryParam(KEY);
    UserSession userSession = UserSession.get();
    userSession.checkComponentPermission(UserRole.CODEVIEWER, fileKey);

    JsonWriter json = response.newJsonWriter();
    json.beginObject();

    DbSession session = dbClient.openSession(false);
    try {
      ComponentDto component = resourceDao.selectComponentByKey(fileKey, session);
      if (component == null) {
        throw new NotFoundException(String.format("Component '%s' does not exists.", fileKey));
      }
      Long projectId = component.projectId();
      Long subProjectId = component.subProjectId();
      // projectId and subProjectId can't be null here
      if (projectId != null && subProjectId != null) {
        appendComponent(json, component, projectId, subProjectId, userSession, session);
        appendPermissions(json, component, userSession);
        appendPeriods(json, projectId, session);
        appendIssuesAggregation(json, component.key(), session);
        appendMeasures(json, fileKey, session);
      }
    } finally {
      MyBatis.closeQuietly(session);
    }

    json.endObject();
    json.close();
  }

  private void appendComponent(JsonWriter json, ComponentDto component, Long projectId, Long subProjectId, UserSession userSession, DbSession session) {
    List<PropertyDto> propertyDtos = propertiesDao.selectByQuery(PropertyQuery.builder()
        .setKey("favourite")
        .setComponentId(component.getId())
        .setUserId(userSession.userId())
        .build(),
      session
    );
    boolean isFavourite = propertyDtos.size() == 1;

    json.prop("key", component.key());
    json.prop("path", component.path());
    json.prop("name", component.name());
    json.prop("q", component.qualifier());

    Component subProject = componentById(subProjectId, session);
    json.prop("subProjectName", subProject.longName());

    Component project = componentById(projectId, session);
    json.prop("projectName", project.longName());

    json.prop("fav", isFavourite);
  }

  private void appendPermissions(JsonWriter json, ComponentDto component, UserSession userSession) {
    json.prop("canMarkAsFavourite", userSession.isLoggedIn() && userSession.hasComponentPermission(UserRole.CODEVIEWER, component.key()));
    json.prop("canBulkChange", userSession.isLoggedIn());
  }

  private void appendMeasures(JsonWriter json, String fileKey, DbSession session) {
    json.name("measures").beginObject();

    List<MeasureDto> measures = measureDao.findByComponentKeyAndMetricKeys(fileKey,
      newArrayList(CoreMetrics.NCLOC_KEY, CoreMetrics.COVERAGE_KEY, CoreMetrics.DUPLICATED_LINES_DENSITY_KEY, CoreMetrics.TECHNICAL_DEBT_KEY, CoreMetrics.VIOLATIONS_KEY,
        CoreMetrics.BLOCKER_VIOLATIONS_KEY, CoreMetrics.MAJOR_VIOLATIONS_KEY, CoreMetrics.MAJOR_VIOLATIONS_KEY, CoreMetrics.MINOR_VIOLATIONS_KEY, CoreMetrics.INFO_VIOLATIONS_KEY),
      session
    );

    json.prop("fNcloc", formattedMeasure(CoreMetrics.NCLOC_KEY, measures));
    json.prop("fCoverage", formattedMeasure(CoreMetrics.COVERAGE_KEY, measures));
    json.prop("fDuplicationDensity", formattedMeasure(CoreMetrics.DUPLICATED_LINES_DENSITY_KEY, measures));
    json.prop("fDebt", formattedMeasure(CoreMetrics.TECHNICAL_DEBT_KEY, measures));
    json.prop("fIssues", formattedMeasure(CoreMetrics.VIOLATIONS_KEY, measures));
    json.prop("fBlockerIssues", formattedMeasure(CoreMetrics.BLOCKER_VIOLATIONS_KEY, measures));
    json.prop("fCriticalIssues", formattedMeasure(CoreMetrics.CRITICAL_VIOLATIONS_KEY, measures));
    json.prop("fMajorIssues", formattedMeasure(CoreMetrics.MAJOR_VIOLATIONS_KEY, measures));
    json.prop("fMinorIssues", formattedMeasure(CoreMetrics.MINOR_VIOLATIONS_KEY, measures));
    json.prop("fInfoIssues", formattedMeasure(CoreMetrics.INFO_VIOLATIONS_KEY, measures));
    json.endObject();
  }

  private void appendPeriods(JsonWriter json, Long projectId, DbSession session) {
    json.name("periods").beginArray();
    SnapshotDto snapshotDto = resourceDao.getLastSnapshotByResourceId(projectId, session);
    if (snapshotDto != null) {
      for (int i = 1; i <= 5; i++) {
        String mode = snapshotDto.getPeriodMode(i);
        if (mode != null) {
          Date periodDate = snapshotDto.getPeriodDate(i);
          String label = periods.label(mode, snapshotDto.getPeriodModeParameter(i), periodDate);
          if (label != null) {
            json.beginArray()
              .value(i)
              .value(label)
              .value(periodDate != null ? DateUtils.formatDateTime(periodDate) : null)
              .endArray();
          }
        }
      }
    }
    json.endArray();
  }

  private void appendIssuesAggregation(JsonWriter json, String componentKey, DbSession session) {
    json.name("severities").beginArray();
    Multiset<String> severities = issueService.findSeveritiesByComponent(componentKey, session);
    for (String severity : severities.elementSet()) {
      json.beginArray()
        .value(severity)
        .value(i18n.message(UserSession.get().locale(), "severity." + severity, null))
        .value(severities.count(severity))
        .endArray();
    }
    json.endArray();

    json.name("rules").beginArray();
    RulesAggregation rulesAggregation = issueService.findRulesByComponent(componentKey, session);
    for (RulesAggregation.Rule rule : rulesAggregation.rules()) {
      json.beginArray()
        .value(rule.ruleKey().toString())
        .value(rule.name())
        .value(rulesAggregation.countRule(rule))
        .endArray();
    }
    json.endArray();
  }

  @CheckForNull
  private Component componentById(@Nullable Long componentId, DbSession session) {
    if (componentId != null) {
      return resourceDao.findById(componentId, session);
    }
    return null;
  }

  @CheckForNull
  private String formattedMeasure(final String metricKey, List<MeasureDto> measures) {
    MeasureDto measure = measureByMetricKey(metricKey, measures);
    if (measure != null) {
      Metric metric = CoreMetrics.getMetric(measure.getKey().metricKey());
      Double value = measure.getValue();
      if (value != null) {
        if (metric.getType().equals(Metric.ValueType.FLOAT)) {
          return i18n.formatDouble(UserSession.get().locale(), value);
        } else if (metric.getType().equals(Metric.ValueType.INT)) {
          return i18n.formatInteger(UserSession.get().locale(), value.intValue());
        } else if (metric.getType().equals(Metric.ValueType.PERCENT)) {
          return i18n.formatDouble(UserSession.get().locale(), value) + "%";
        } else if (metric.getType().equals(Metric.ValueType.WORK_DUR)) {
          return durations.format(UserSession.get().locale(), durations.create(value.longValue()), Durations.DurationFormat.SHORT);
        }
      }
    }
    return null;
  }

  @CheckForNull
  private static MeasureDto measureByMetricKey(final String metricKey, List<MeasureDto> measures) {
    return Iterables.find(measures, new Predicate<MeasureDto>() {
      @Override
      public boolean apply(@Nullable MeasureDto input) {
        return input != null && metricKey.equals(input.getKey().metricKey());
      }
    }, null);
  }

}