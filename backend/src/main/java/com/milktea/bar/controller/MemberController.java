package com.milktea.bar.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.milktea.bar.common.ApiResponse;
import com.milktea.bar.entity.Member;
import com.milktea.bar.service.MemberService;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<Member>> list(@RequestParam(required = false) String level) {
        return ApiResponse.success(memberService.list(level));
    }

    @PostMapping
    public ApiResponse<Member> create(@RequestBody Member input) {
        return ApiResponse.success(memberService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Member> update(@PathVariable Long id, @RequestBody Member input) {
        return ApiResponse.success(memberService.update(id, input));
    }

    @PostMapping("/{id}/points")
    public ApiResponse<Member> addPoints(@PathVariable Long id, @RequestParam int delta) {
        return ApiResponse.success(memberService.addPoints(id, delta));
    }
}
