package io.ulzha.spive.basicrunner.api;

import io.ulzha.spive.lib.umbilical.HistoryBuffer.Iopw;
import java.util.List;

public record GetThreadGroupIopwsResponse(List<Iopw> iopws) {}
