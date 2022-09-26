package com.example.sm.auth.repository;

import com.example.sm.auth.decorator.*;
import com.example.sm.common.decorator.*;
import com.example.sm.auth.enums.UserSortBy;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class UserCustomRepositoryImpl implements UserCustomRepository {

    @Autowired
    MongoTemplate mongoTemplate;


    @Override
    public List<UserResponse> findAllUserByFilterAndSortAndPage(UserFilter filter, FilterSortRequest.SortRequest<UserSortBy> sort, PageRequest pagination) {

        List<AggregationOperation> operations = userFilterAggregation(filter, sort, pagination, true);


        //Created Aggregation operation
        Aggregation aggregation = newAggregation(operations);

        List<UserResponse> users = mongoTemplate.aggregate(aggregation, "users", UserResponse.class).getMappedResults();

        // Find Count
        List<AggregationOperation> operationForCount = userFilterAggregation(filter, sort, pagination, false);
        operationForCount.add(group().count().as("count"));
        operationForCount.add(project("count"));
        Aggregation aggregationCount = newAggregation(UserResponse.class, operationForCount);
        AggregationResults<CountQueryResult> countQueryResults = mongoTemplate.aggregate(aggregationCount, "users", CountQueryResult.class);
        long count = countQueryResults.getMappedResults().size() == 0 ? 0 : countQueryResults.getMappedResults().get(0).getCount();
        return users;
    }

    //create list
    //match user entered value and databasevalue(use: getCriteria method)
    //if addpage true then perfom sorting
    //return list

    private List<AggregationOperation> userFilterAggregation(UserFilter filter, FilterSortRequest.SortRequest<UserSortBy> sort, PageRequest pagination, boolean addPage) {
        List<AggregationOperation> operations = new ArrayList<>();

        operations.add(match(getCriteria(filter, operations)));

        if (addPage) {
            //sorting
            if (sort != null && sort.getSortBy() != null && sort.getOrderBy() != null) {
                operations.add(new SortOperation(Sort.by(sort.getOrderBy(), sort.getSortBy().getValue())));
            }
            if (pagination != null) {
                operations.add(skip(pagination.getOffset()));
                operations.add(limit(pagination.getPageSize()));
            }
        }
        return operations;
    }
    private List<AggregationOperation> userFilterAggregations( UserIdsRequest userIdsRequest,FilterSortRequest.SortRequest<UserSortBy> sort,PageRequest pagination,boolean addPage) {
        List<AggregationOperation> operations = new ArrayList<>();
        if (addPage) {
            //sorting
            if (sort != null && sort.getSortBy() != null && sort.getOrderBy() != null) {
                operations.add(new SortOperation(Sort.by(sort.getOrderBy(), sort.getSortBy().getValue())));
            }
            if (pagination != null) {
                operations.add(skip(pagination.getOffset()));
                operations.add(limit(pagination.getPageSize()));
            }
        }
        return operations;
    }
    private Criteria getCriteria(UserFilter userFilter, List<AggregationOperation> operations) {
        Criteria criteria = new Criteria();
        operations.add(new CustomAggregationOperation(
                new Document("$addFields",
                        new Document("search",
                                new Document("$concat", Arrays.asList(
                                        new Document("$ifNull", Arrays.asList("$userName", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$password", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$email", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$lastName", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$middleName", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$firstName", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$address.address1", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$address.address2", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$address.address3", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$address.city", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$address.state", "")),
                                        "|@|", new Document("$ifNull", Arrays.asList("$address.zipCode", "")))
                                )
                        )
                ))
        );

        if (!StringUtils.isEmpty(userFilter.getSearch())) {
            userFilter.setSearch(userFilter.getSearch().replaceAll("\\|@\\|", ""));
            userFilter.setSearch(userFilter.getSearch().replaceAll("\\|@@\\|", ""));
            criteria = criteria.orOperator(
                    Criteria.where("search").regex(".*" + userFilter.getSearch() + ".*", "i")
            );
        }
        if (!StringUtils.isEmpty(userFilter.getId())) {
            criteria = criteria.and("_id").in(userFilter.getId());
        }
        if (userFilter.getRole() != null) {
            criteria = criteria.and("role").is(userFilter.getRole());
        }

        criteria = criteria.and("softDelete").is(false);
        return criteria;
    }

    //query obj
    //criteria add in query
    //use find method of mongo template
    public List<UserResponse> getUser(UserFilter userFilter) {
        Criteria criteria = new Criteria();
        List<UserResponse> userResponse;
        if (userFilter.getRole() != null) {
            criteria = criteria.and("role").is(userFilter.getRole());
        }
        criteria = criteria.and("softDelete").is(false);
        Query query = new Query();
        query.addCriteria(criteria);
        userResponse = mongoTemplate.find(query, UserResponse.class, "users");
        System.out.println(userResponse.size());
        System.out.println(userResponse);
        return userResponse;
    }


    private List<AggregationOperation> userDetails(UserDetail userDetail){
        List<AggregationOperation> operations = new ArrayList<>();

        Criteria criteria = new Criteria();

        criteria = criteria.and("_id").in(userDetail.getUserIds());
        criteria = criteria.and("softDelete").is(false);

        operations.add(match(criteria));

        operations.add(unwind("results"));

        operations.add(match(Criteria.where("results.semesterRegex").is(userDetail.getSemester())));

        return operations;
    }

    private List<AggregationOperation> userResultBySemesters(UserResult userResult){

        List<AggregationOperation> operations = new ArrayList<>();

        Criteria criteria = new Criteria();

        criteria = criteria.and("_id").in(userResult.getUserIds());
        criteria = criteria.and("softDelete").is(false);

        operations.add(match(criteria));
        operations.add(unwind("results"));
        operations.add(match(Criteria.where("results.semesterRegex").in(userResult.getSemester())));

        operations.add(new CustomAggregationOperation( new Document("$group",
                new Document("_id","$_id")
                        .append("totalMark",
                                new Document("$sum","$results.spi"))
                        .append("average",
                                new Document("$avg","$results.spi"))
                        .append("count",
                                new Document("$sum",1))
                        .append("fullName",
                                new Document("$last","$fullName"))
                        .append("results",
                                new Document("$push",
                                        new Document("semesterRegex" ,"$results.semesterRegex")
                                                .append("spi" ,"$results.spi")
                                )))));
        return operations;
    }

    private List<AggregationOperation> userResultByMinMaxMarks(UserIdsRequest userIdsRequest) {
        List<AggregationOperation> operations = new ArrayList<>();

        Criteria criteria = new Criteria();

        criteria = criteria.and("_id").in(userIdsRequest.getUserId());
        criteria = criteria.and("softDelete").is(false);

        operations.add(match(criteria));
        operations.add(new CustomAggregationOperation( new Document("$set",
                        new Document()
                        .append("max",
                                new Document("$max","$results.spi"))
                        .append("min",
                                new Document("$min","$results.spi")))));

        operations.add(new CustomAggregationOperation( new Document("$project",
                new Document()
                        .append("maxArray",
                                new Document("$filter",new Document()
                                        .append("input","$results")
                                        .append("as","result")
                                        .append( "cond",
                                                new Document("$eq",
                                                        Arrays.asList("$$result.spi", "$max")
                                                ))))
                        .append("minArray",
                                new Document("$filter",new Document()
                                        .append("input","$results")
                                        .append("as","result")
                                        .append( "cond",
                                                new Document("$eq",
                                                        Arrays.asList("$$result.spi", "$min")
                              ))))
                        .append("fullName",1)
                        .append("max",1)
                        .append("min",1)
        )));
        operations.add(new CustomAggregationOperation( new Document("$unwind",
                new Document().
                        append ("path" ,"$maxArray")
                .append("preserveNullAndEmptyArrays" ,false
                ))));

        operations.add(new CustomAggregationOperation( new Document("$unwind",
                new Document().
                        append ("path" ,"$minArray")
                        .append("preserveNullAndEmptyArrays" ,false
                        ))));

        operations.add(new CustomAggregationOperation (new Document("$group",
                new Document("_id","$_id")
                        .append("maxArray",
                                new Document("$first","$maxArray"))
                        .append("minArray",
                                new Document("$first","$minArray"))
                        .append("max",
                                new Document("$first","$max"))
                        .append("min",
                                new Document("$first","$min"))
                        .append("fullName",
                                new Document("$first","$fullName"))
        )));
        return  operations;
    }

    private List<AggregationOperation>  userResultsByDate(UserResultByDate userResultByDate){
     List<AggregationOperation> operations = new ArrayList<>();

     Criteria criteria = new Criteria();

          criteria = criteria.and("softDelete").is(false);
          operations.add(match(criteria));

          operations.add(unwind("results"));

          operations.add(new CustomAggregationOperation( new Document("$set",
                        new Document()
                                .append("resultDate",
                                        new Document("$substr",
                        Arrays.asList("$results.date",0.0,10.0))))));

          operations.add(new CustomAggregationOperation( new Document("$set",
                new Document()
                        .append("resultOfDate",
                                new Document("$cond",
                                        new Document("if",
                                         new Document("$eq",
                                        Arrays.asList("$resultDate",userResultByDate.getDate())))
                                                .append("then",1.0)
                                                .append("else",0.0)
                                )))));

            operations.add(new CustomAggregationOperation( new Document("$match",
                new Document("resultOfDate" , 1.0))));

             operations.add(new CustomAggregationOperation (new Document("$group",
                 new Document("_id","$_id")
                        .append("fullName",
                                new Document("$first","$fullName"))
                        .append("resultOfDate",
                                new Document("$first","$resultOfDate"))
                        .append("resultDate",
                                new Document("$first","$resultDate"))
                        .append("result",
                                new Document("$push",
                                        new Document("semester" ,"$results.semester")
                                                .append("spi" ,"$results.spi")
                                                .append("date","$results.date")
                                                .append("year","$results.year")
                                )))));

        return operations;
    }

    private List<AggregationOperation> userResultsByStatus(UserIdsRequest userIdsRequest,FilterSortRequest.SortRequest<UserSortBy> sort, PageRequest pagination,boolean addPage){
        List<AggregationOperation> operations = new ArrayList<>();

        Criteria criteria = new Criteria();

        criteria = criteria.and("_id").in(userIdsRequest.getUserId());
        criteria = criteria.and("softDelete").is(false);

        operations.add(match(criteria));

        operations.add(unwind("results"));

        operations.add(new CustomAggregationOperation( new Document("$project",
          new Document("status",
                      new Document("$switch",
                              new Document("branches",
                                  Arrays.asList(
                                     new Document("case",new Document("$eq",
                                           Arrays.asList("$results.spi",10.0)))
                                            .append("then","firstClass"),

                                   new Document("case",
                                    new Document("$lt",
                                        Arrays.asList("$results.spi",4.0)))
                                        .append("then","fail"),

                              new Document("case",
                                   new Document("$and",
                                         Arrays.asList(new Document("$lt",
                                              Arrays.asList("$results.spi",10.0)),
                                                    new Document("$gte",
                                                      Arrays.asList("$results.spi",9.0)))))
                                                      .append("then","secondClass"),

                                          new Document("case",
                                                  new Document("$and",
                                                          Arrays.asList(new Document("$lt",
                                                                Arrays.asList("$results.spi",9.0)),
                                                                  new Document("$gte",
                                                                     Arrays.asList("$results.spi",8.0)))))
                                                                     .append("then","thirdClass"),

                                          new Document("case",
                                                  new Document("$and",
                                                          Arrays.asList(new Document("$lt",
                                                                Arrays.asList("$results.spi",8.0)),
                                                                  new Document("$gte",
                                                                      Arrays.asList("$results.spi",7.0)))))
                                                                      .append("then","fourthClass"),
                                          new Document("case",
                                                  new Document("$and",
                                                          Arrays.asList(new Document("$lt",
                                                                          Arrays.asList("$results.spi",7.0)),
                                                                  new Document("$gte",
                                                                          Arrays.asList("$results.spi",4.0)))))
                                                  .append("then","fifthClass")))
                                          .append("default","none")))
                              .append("fullName",1.0)
                              .append("results",1.0))));

        operations.add(new CustomAggregationOperation (new Document("$group",
                new Document("_id","$_id")
                        .append("fullName",
                                new Document("$first","$fullName"))
                        .append("result",
                                new Document("$push",
                                        new Document("semester" ,"$results.semester")
                                                .append("spi" ,"$results.spi")
                                                .append("date","$results.date")
                                                .append("year","$results.year")
                                                .append("status","$status")
                                )))));


        if (addPage) {
            //sorting
            if (sort != null && sort.getSortBy() != null && sort.getOrderBy() != null) {
                operations.add(new SortOperation(Sort.by(sort.getOrderBy(), sort.getSortBy().getValue())));
            }
            if (pagination != null) {
                operations.add(skip(pagination.getOffset()));
                operations.add(limit(pagination.getPageSize()));
            }
        }

        return operations;
    }

    public List<UserDetailResponse> getUserResult(UserDetail userDetail) {
        List<AggregationOperation> operations = userDetails(userDetail);
        Aggregation aggregation = newAggregation(operations);
        List<UserDetailResponse> userDetailResponse;
        userDetailResponse = mongoTemplate.aggregate(aggregation, "users", UserDetailResponse.class).getMappedResults();
        return userDetailResponse;
    }

    public List<UserResultResponse> getUserResultBySemester(UserResult userResult) {
        List<AggregationOperation> operations = userResultBySemesters(userResult);
        Aggregation aggregation = newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "users",UserResultResponse.class).getMappedResults();
    }

    @Override
    public List<UserMinMaxMarkSemResponse> getUserResultByMinMaxMark(UserIdsRequest userIdsRequest) {
        List<AggregationOperation> operations =  userResultByMinMaxMarks(userIdsRequest);
        Aggregation aggregation = newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "users",UserMinMaxMarkSemResponse.class).getMappedResults();
    }

    @Override
    public List<UserResultByDateRespose> getUserResultByDate(UserResultByDate userResultByDate) {
        List<AggregationOperation> operations =  userResultsByDate(userResultByDate);
        Aggregation aggregation = newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "users",UserResultByDateRespose.class).getMappedResults();
    }

    @Override
    public List<UserResultByStatus> getUserResultByStatus(UserIdsRequest userIdsRequest) {
        List<AggregationOperation> operations =  userResultsByStatus(userIdsRequest,null,null,false);
        Aggregation aggregation = newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, "users",UserResultByStatus.class).getMappedResults();
    }

    @Override
    public Page<UserResultByStatus> findUserResultStatusByFilterAndSortAndPage(UserIdsRequest userIdsRequest, FilterSortRequest.SortRequest<UserSortBy> sort, PageRequest pagination) throws InvocationTargetException, IllegalAccessException {
        List<AggregationOperation> operations = userResultsByStatus(userIdsRequest, sort, pagination,true);
        //Created Aggregation operation
        Aggregation aggregation = newAggregation(operations);

        List<UserResultByStatus> users = mongoTemplate.aggregate(aggregation, "users",UserResultByStatus.class).getMappedResults();

        List<AggregationOperation> operationForCount = userResultsByStatus(userIdsRequest, sort, pagination, false);
        operationForCount.add(group().count().as("count"));
        operationForCount.add(project("count"));
        Aggregation aggregationCount = newAggregation(UserResultByStatus.class, operationForCount);
        AggregationResults<CountQueryResult> countQueryResults = mongoTemplate.aggregate(aggregationCount, "users", CountQueryResult.class);
        long count = countQueryResults.getMappedResults().size() == 0 ? 0 : countQueryResults.getMappedResults().get(0).getCount();
        return PageableExecutionUtils.getPage(
               users,
               pagination,
                () -> count);
    }

}





