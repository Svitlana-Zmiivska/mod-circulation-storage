package org.folio.rest.impl;

import static org.folio.rest.impl.Headers.TENANT_HEADER;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FixedDueDateSchedule;
import org.folio.rest.jaxrs.model.FixedDueDateSchedules;
import org.folio.rest.jaxrs.model.Schedule;
import org.folio.rest.jaxrs.resource.FixedDueDateScheduleStorageResource;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class FixedDueDateSchedulesAPI implements FixedDueDateScheduleStorageResource {

  private static final Logger       log            = LoggerFactory.getLogger(FixedDueDateSchedulesAPI.class);
  private static final String       SCHEMA_NAME    = "apidocs/raml/fixed-due-date-schedule.json";

  private static String             schema      =  null;

  private final String FIXED_SCHEDULE_TABLE     = "fixed_due_date_schedule";

  private final Class<FixedDueDateSchedule> DUE_DATE_SCHEDULE_CLASS = FixedDueDateSchedule.class;


  public FixedDueDateSchedulesAPI(Vertx vertx, String tenantId) {
    if(schema == null){
      initCQLValidation();
    }
  }

  private void initCQLValidation() {
    String path = SCHEMA_NAME;
    try {
      schema = IOUtils.toString(getClass().getClassLoader().getResourceAsStream(path), "UTF-8");
    } catch (Exception e) {
      log.error("unable to load schema - " +path+ ", validation of query fields will not be active");
    }
  }

  @Override
  @Validate
  public void deleteFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      Map<String,
      String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    vertxContext.runOnContext(v -> {
      try {
        PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
            TenantTool.calculateTenantId(tenantId));

        postgresClient.mutate(
            String.format("TRUNCATE TABLE %s_%s.%s", tenantId, PomReader.INSTANCE.getModuleName(), FIXED_SCHEDULE_TABLE), reply -> {
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .noContent().build()));
            });
      } catch (Exception e) {
        log.error(e);
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            FixedDueDateScheduleStorageResource.DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                .withPlainInternalServerError(e.getMessage())));
      }
    });
  }

  @Override
  public void getFixedDueDateScheduleStorageFixedDueDateSchedules(
      int offset,
      int limit,
      String query,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      vertxContext.runOnContext(v -> {
        try {
          PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
              TenantTool.calculateTenantId(tenantId));

          String[] fieldList = { "*" };

          CQLWrapper cql = getCQL(query, limit, offset, schema);
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, fieldList, cql, true, false, reply -> {
            try {
              if (reply.succeeded()) {
                List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result()[0];

                FixedDueDateSchedules pagedSchedules = new FixedDueDateSchedules();
                pagedSchedules.setFixedDueDateSchedules(dueDateSchedules);
                pagedSchedules.setTotalRecords((Integer) reply.result()[1]);

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withJsonOK(pagedSchedules)));
              } else {
                log.error(reply.cause());
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withPlainInternalServerError(reply.cause().getMessage())));
              }
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                      .withPlainInternalServerError(e.getMessage())));
            }
          });
        }
        catch(CQLQueryValidationException e1){
          String field = e1.getMessage();
          int start = field.indexOf('\'');
          int end = field.lastIndexOf('\'');
          if(start != -1 && end != -1){
            field = field.substring(start+1, end);
          }
          log.error(e1.getMessage());
          Errors e = ValidationHelper.createValidationErrorMessage(field, "", e1.getMessage());
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
            .withJsonUnprocessableEntity(e)));
        }
        catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void postFixedDueDateScheduleStorageFixedDueDateSchedules(
      String lang,
      FixedDueDateSchedule entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {

      if(entity != null && !isDateRangeValid(entity.getSchedules())){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .withJsonUnprocessableEntity(new Errors())));
        return;
      }

      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      vertxContext.runOnContext(v -> {
        try {
          if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
          }

          postgresClient.save(FIXED_SCHEDULE_TABLE, entity.getId(), entity, reply -> {
            try {
              if (reply.succeeded()) {
                OutStream stream = new OutStream();
                stream.setData(entity);

                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withJsonCreated(reply.result(), stream)));
              } else {
                log.error(reply.cause());
                if(isUniqueViolation(reply.cause())){
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withPlainBadRequest(PgExceptionUtil.badRequestMessage(reply.cause()))));
                }
                else{
                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                        .withPlainInternalServerError(reply.cause().getMessage())));
                }
              }
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                    .withPlainInternalServerError(e.getMessage())));
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void getFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria(SCHEMA_NAME);

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(fixedDueDateScheduleId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, criterion, true, false, reply -> {
            try {
              if (reply.succeeded()) {
                List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result()[0];

                if (dueDateSchedules.size() == 1) {
                  FixedDueDateSchedule dueDateSchedule = dueDateSchedules.get(0);

                  asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                      FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withJsonOK(dueDateSchedule)));
                } else {
                  asyncResultHandler.handle(Future.succeededFuture(
                      FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withPlainNotFound(PgExceptionUtil.badRequestMessage(reply.cause()))));
                }
              } else {
                log.error(reply.cause());
                asyncResultHandler.handle(Future.succeededFuture(
                    FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                        .withPlainInternalServerError(reply.cause().getMessage())));

              }
            } catch (Exception e) {
              log.error(e);
              asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                  FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainInternalServerError(e.getMessage())));
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.GetFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void deleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      Map<String,
      String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {
      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(fixedDueDateScheduleId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.delete(FIXED_SCHEDULE_TABLE, criterion, reply -> {
            if (reply.succeeded()) {
              asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withNoContent()));
            } else {
              log.error(reply.cause());
              if(isBadId(reply.cause())){
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainNotFound(PgExceptionUtil.badRequestMessage(reply.cause()))));
              }
              else{
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainInternalServerError(reply.cause().getMessage())));
              }
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(Future
              .succeededFuture(DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(
          Future.succeededFuture(DeleteFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  @Override
  @Validate
  public void putFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleId(
      String fixedDueDateScheduleId,
      String lang,
      FixedDueDateSchedule entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext
      ) throws Exception {

    String tenantId = okapiHeaders.get(TENANT_HEADER);

    try {

      if(entity != null && !isDateRangeValid(entity.getSchedules())){
        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
          FixedDueDateScheduleStorageResource.PostFixedDueDateScheduleStorageFixedDueDateSchedulesResponse
              .withJsonUnprocessableEntity(new Errors())));
        return;
      }

      PostgresClient postgresClient = PostgresClient.getInstance(vertxContext.owner(),
          TenantTool.calculateTenantId(tenantId));

      Criteria a = new Criteria();

      a.addField("'id'");
      a.setOperation("=");
      a.setValue(fixedDueDateScheduleId);

      Criterion criterion = new Criterion(a);

      vertxContext.runOnContext(v -> {
        try {
          postgresClient.get(FIXED_SCHEDULE_TABLE, DUE_DATE_SCHEDULE_CLASS, criterion, true, false, reply -> {
            if (reply.succeeded()) {
              List<FixedDueDateSchedule> dueDateSchedules = (List<FixedDueDateSchedule>) reply.result()[0];

              if (dueDateSchedules.size() == 1) {
                try {
                  postgresClient.update(FIXED_SCHEDULE_TABLE, entity, criterion, true, update -> {
                    try {
                      if (update.succeeded()) {
                        OutStream stream = new OutStream();
                        stream.setData(entity);

                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withNoContent()));
                      } else {
                        log.error(update.cause());
                        if(isUniqueViolation(update.cause())){
                          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
                            FixedDueDateScheduleStorageResource.
                              PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withPlainBadRequest(PgExceptionUtil.badRequestMessage(update.cause()))));
                        }
                        else{
                          log.error(update.cause());
                          asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withPlainInternalServerError(update.cause().getMessage())));
                        }
                      }
                    } catch (Exception e) {
                      log.error(e);
                      asyncResultHandler.handle(Future.succeededFuture(
                          PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                    }
                  });
                } catch (Exception e) {
                  log.error(e);
                  asyncResultHandler.handle(Future.succeededFuture(
                      PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withPlainInternalServerError(e.getMessage())));
                }
              } else {
                try {
                  postgresClient.save(FIXED_SCHEDULE_TABLE, entity.getId(), entity, save -> {
                    try {
                      if (save.succeeded()) {
                        OutStream stream = new OutStream();
                        stream.setData(entity);

                        asyncResultHandler.handle(Future.succeededFuture(
                            PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                .withNoContent()));
                      } else {
                          log.error(save.cause());
                          asyncResultHandler.handle(Future.succeededFuture(
                              PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                                  .withPlainInternalServerError(save.cause().getMessage())));
                      }
                    } catch (Exception e) {
                      log.error(e);
                      asyncResultHandler.handle(Future.succeededFuture(
                          PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                              .withPlainInternalServerError(e.getMessage())));
                    }
                  });
                } catch (Exception e) {
                    log.error(e);
                    asyncResultHandler.handle(Future.succeededFuture(
                      PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                          .withPlainInternalServerError(e.getMessage())));
                }
              }
            } else {
              log.error(reply.cause());
              if(isBadId(reply.cause())){
                asyncResultHandler.handle(Future
                  .succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainNotFound(PgExceptionUtil.badRequestMessage(reply.cause()))));
              }
              else{
                asyncResultHandler.handle(Future
                  .succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                      .withPlainInternalServerError(reply.cause().getMessage())));
              }
            }
          });
        } catch (Exception e) {
          log.error(e);
          asyncResultHandler.handle(
              Future.succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
                  .withPlainInternalServerError(e.getMessage())));
        }
      });
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(
          Future.succeededFuture(PutFixedDueDateScheduleStorageFixedDueDateSchedulesByFixedDueDateScheduleIdResponse
              .withPlainInternalServerError(e.getMessage())));
    }
  }

  private CQLWrapper getCQL(String query, int limit, int offset, String schema) throws Exception {
    CQL2PgJSON cql2pgJson = null;
    if(schema != null){
      cql2pgJson = new CQL2PgJSON("fixed_due_date_schedule.jsonb", schema);
    } else {
      cql2pgJson = new CQL2PgJSON("fixed_due_date_schedule.jsonb");
    }
    return new CQLWrapper(cql2pgJson, query).setLimit(new Limit(limit)).setOffset(new Offset(offset));
  }

  private boolean isDateRangeValid(List<Schedule> schedules) {
    try {
      //String from, String to, String due
      int size = schedules.size();
      for (int i = 0; i < size; i++) {
        Date dueDate = schedules.get(i).getDue();
        Date fromDate = schedules.get(i).getFrom();
        Date toDate = schedules.get(i).getTo();

        if(!fromDate.before(dueDate) || !fromDate.before(toDate) || !toDate.before(dueDate)){
          log.info(
            "Unable to save fixed loan date. Date range not valid. Due date: " + schedules.get(i).getDue()
            + " , from date: " + schedules.get(i).getFrom()
            + " , to date: " + schedules.get(i).getTo());
          return false;
        }
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return true;
  }

  private boolean isUniqueViolation(Throwable e){
    if(e != null && e.getMessage().contains("duplicate key value violates unique constraint")){
      return true;
    }
    return false;
  }

  private boolean isBadId(Throwable e){
    if(e != null && e.getMessage().contains("invalid input syntax for type numeric")){
      return true;
    }
    return false;
  }
}
