package dev.scx.data.sql.test;

import dev.scx.array.ScxArray;
import dev.scx.data.sql.SQLRepository;
import dev.scx.data.sql.test.entity.Car;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static dev.scx.data.aggregation.AggregationBuilder.agg;
import static dev.scx.data.aggregation.AggregationBuilder.groupBy;
import static dev.scx.data.field_policy.FieldPolicyBuilder.exclude;
import static dev.scx.data.field_policy.FieldPolicyBuilder.virtualField;
import static dev.scx.data.query.QueryBuilder.eq;
import static dev.scx.data.query.QueryBuilder.lt;
import static dev.scx.data.sql.test.SQLTestKit.recreateTable;
import static dev.scx.data.sql.test.SQLTestKit.sqlClient;
import static org.testng.Assert.assertEquals;

public class CarRepositoryTest {

    public static SQLRepository<Car> carRepository;

    static void main(String[] args) throws SQLException {
        beforeTest();
        testAll();
    }

    @BeforeTest
    public static void beforeTest() throws SQLException {
        carRepository = new SQLRepository<>(Car.class, sqlClient);
        recreateTable(carRepository);
    }

    @Test
    public static void testAll() throws SQLException {
        testAdd();
        testAdd2();
        testFind();
        testAgg();
        test3();
    }

    public static void testAdd() throws SQLException {
        var s = new ArrayList<Long>();
        // 测试单条插入
        for (int i = 0; i < 10; i = i + 1) {
            var car = new Car();
            car.name = ScxArray.randomGet("奔驰", "宝马", "奥迪");
            car.size = i;
            car.city = "city" + i;
            // 测试混合插入
            Long id = carRepository.add(car, exclude("city").assignField("color", """
                CONCAT('#',
                    LPAD(HEX(FLOOR(RAND() * 256)), 2, '0'),
                    LPAD(HEX(FLOOR(RAND() * 256)), 2, '0'),
                    LPAD(HEX(FLOOR(RAND() * 256)), 2, '0')
                  )
                """));
            s.add(id);
        }
        assertEquals(s, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

        //测试列表查询 应该有 color 但是没有 city
        List<Car> cars = carRepository.find();
        for (Car car : cars) {
            if (car.city != null || car.color == null) {
                Assert.fail();
            }
        }
        //测试 map 查询 这里测试的是 map 是否和 entity 的字段名相符而不是数据库中的名字
        var carMaps = carRepository.finder().listMap();
        for (var carMAP : carMaps) {
            if (carMAP.get("city") != null || carMAP.get("color") == null || carMAP.get("size") == null) {
                Assert.fail();
            }
        }
        carRepository.clear();
    }

    public static void testAdd2() {
        // 测试批量插入
        var carList = new ArrayList<Car>();
        for (int i = 0; i < 10; i = i + 1) {
            var car = new Car();
            car.name = ScxArray.randomGet("奔驰", "宝马", "奥迪");
            car.size = i;
            carList.add(car);
        }
        List<Long> add = carRepository.add(carList, exclude("id"));
        assertEquals(add, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
    }

    public static void testFind() {
        var carList = new ArrayList<Car>();
        carRepository.finder().forEach(c -> {
            carList.add(c);
        });
        assertEquals(carList.size(), 10);
        var cars = carRepository.finder(virtualField("name", "REVERSE(name)")).listMap();
        assertEquals(cars.size(), 10);
    }

    public static void testAgg() {
        var list1 = carRepository.aggregate(lt("id", 3), groupBy("name").agg("totalSize", "SUM(size)"), eq("name", "奔驰"));
        var list2 = carRepository.aggregateFirst(agg("totalSize", "SUM(size)"));
        System.out.println(list2);
    }

    public static void test3() {
        // 测试 0 条数据批量插入
        carRepository.add(List.of());
        // 测试 1 条 null 数据批量插入
        var carList = new ArrayList<Car>();
        carList.add(null);
        carRepository.add(carList);
    }

}
