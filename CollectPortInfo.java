import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CollectPortInfo extends Verilog2001BaseListener {
  ArrayList<Module> modules = new ArrayList<>();
  private Module currModule = null;
  private PortDir currDir = PortDir.NA;
  private String width = "1";
  private boolean isUnderGlobalParam = false;
  private final HashSet<String> clocks = new HashSet<>();

  private void reset() {
    currDir = PortDir.NA;
    width = "1";
    isUnderGlobalParam = false;
  }

  @Override
  public void enterModule_declaration(Verilog2001Parser.Module_declarationContext ctx) {
    currModule = new Module(ctx.module_identifier().getText());
    clocks.clear();
  }

  @Override
  public void exitModule_declaration(Verilog2001Parser.Module_declarationContext ctx) {
    currModule.ports.forEach(p -> {
      if (clocks.contains(p.name)) {
        p.toClock();
      }
    });
    modules.add(currModule);
    currModule = null;
  }

  @Override
  public void enterInout_declaration(Verilog2001Parser.Inout_declarationContext ctx) {
    currDir = PortDir.INOUT;
  }

  @Override
  public void exitInout_declaration(Verilog2001Parser.Inout_declarationContext ctx) {
    reset();
  }

  @Override
  public void enterInput_declaration(Verilog2001Parser.Input_declarationContext ctx) {
    currDir = PortDir.INPUT;
  }

  @Override
  public void exitInput_declaration(Verilog2001Parser.Input_declarationContext ctx) {
    reset();
  }

  @Override
  public void enterOutput_declaration(Verilog2001Parser.Output_declarationContext ctx) {
    currDir = PortDir.OUTPUT;
  }

  @Override
  public void exitOutput_declaration(Verilog2001Parser.Output_declarationContext ctx) {
    reset();
  }

  @Override
  public void enterPort_identifier(Verilog2001Parser.Port_identifierContext ctx) {
    if (currDir != PortDir.NA) {
      currModule.ports.add(new Port(currDir, ctx.getText(), width, ""));
    }
  }

  @Override
  public void enterRange(Verilog2001Parser.RangeContext ctx) {
    if (currDir != PortDir.NA) {  // Guarantee we are in port declaration context.
      String lsb = ctx.lsb_constant_expression().getText();
      String msb = ctx.msb_constant_expression().getText();
      assert(lsb.equals("0"));  // For the most common case.
      if (msb.matches("\\d+")) {  // Directly calc constant.
        width = Integer.toString((Integer.parseInt(msb) + 1));
      }
      else {
        /// TODO Convert `WIDTH - 1 + 1` into `WIDTH`.
        width = msb + " + 1";
      }
    }
  }

  @Override
  public void enterParameter_declaration_(Verilog2001Parser.Parameter_declaration_Context ctx) {
    isUnderGlobalParam = true;
  }

  @Override
  public void enterList_of_param_assignments(Verilog2001Parser.List_of_param_assignmentsContext ctx) {
    if (isUnderGlobalParam) {
      List<Verilog2001Parser.Param_assignmentContext> params = ctx.param_assignment();
      currModule.params.addAll(params.stream()
          .map(p -> new Parameter(p.parameter_identifier().getText(), p.constant_expression().getText()))
          .collect(Collectors.toList()));
    }
  }

  @Override
  public void exitParameter_declaration_(Verilog2001Parser.Parameter_declaration_Context ctx) {
    reset();
  }

  private static Pattern clockPattern = Pattern.compile(".*clk.*", Pattern.CASE_INSENSITIVE);
  @Override
  public void enterEvent_primary(Verilog2001Parser.Event_primaryContext ctx) {
    String maybeModifier = ctx.getStart().getText();
    if ((maybeModifier.equals("posedge") || maybeModifier.equals("negedge"))
        && clockPattern.matcher(ctx.expression().getText()).matches()) {
      clocks.add(ctx.expression().getText());
    }
  }
}