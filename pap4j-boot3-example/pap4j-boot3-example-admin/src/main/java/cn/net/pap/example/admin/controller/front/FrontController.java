package cn.net.pap.example.admin.controller.front;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/front/api")
@Tag(name = "前台接口", description = "前台相关的API接口")
public class FrontController {

    @GetMapping("/front_hello")
    @Operation(summary = "前台欢迎接口", description = "返回前台欢迎信息", operationId = "front_hello")
    public String front_hello() {
        return "Hello from Front API!";
    }

    /**
     * 起点接口：创建用户
     * 注意：operationId 必须全局唯一，Link 依靠它来寻找目标
     */
    @Operation(summary = "创建新用户", operationId = "createUser", responses = {@ApiResponse(responseCode = "201", description = "用户创建成功", content = @Content(schema = @Schema(implementation = UserResponse.class)), links = {@Link(name = "GetUserDetails", // Link 的名称，在 UI 上显示的按钮名
            operationId = "getUserById", // 目标接口的 operationId
            parameters = {
                    // 映射规则：将当前接口返回体中的 id 字段 ($response.body#/id)
                    // 传递给目标接口的参数名 userId
                    @LinkParameter(name = "userId", expression = "$response.body#/id")}, description = "通过返回的 ID 直接跳转到查询接口")})})
    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@RequestBody UserRequest request) {
        String newId = UUID.randomUUID().toString();
        UserResponse response = new UserResponse(newId, request.name());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * 终点接口：查询用户
     */
    @Operation(summary = "根据ID获取用户详情", operationId = "getUserById" // 被上面的 Link 引用
    )
    @GetMapping("/users/{userId}")
    public UserResponse getUserById(@Parameter(description = "用户ID") @PathVariable String userId) {
        return new UserResponse(userId, "张三 (模拟数据)");
    }

    /**
     * switchByParam
     * @param picPath
     * @return
     */
    @GetMapping("/switchByParam")
    public ResponseEntity switchByParam(@RequestParam String picPath) {
        String ext = picPath.substring(picPath.lastIndexOf('.') + 1).toLowerCase();

        switch (ext) {
            case "jpg":
            case "jpeg":
                return new ResponseEntity<>("jpg", HttpStatus.OK);
            case "tif":
            case "tiff":
                return new ResponseEntity<>("tiff", HttpStatus.OK);
            default:
                return new ResponseEntity<>("default", HttpStatus.OK);
        }
    }

    // --- DTO 内部类 ---
    @Schema(description = "创建用户请求体")
    public record UserRequest(
            @Schema(description = "用户名称", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
            String name
    ) {
    }

    @Schema(description = "用户响应结构体")
    public record UserResponse(
            @Schema(description = "用户唯一 ID", example = "d87a412c-9a4f-4d9d-9b16-56cddfe8d8d8")
            String id,
            @Schema(description = "用户名称", example = "张三")
            String name
    ) {
    }

}
