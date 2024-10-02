//
// Copyright (c) 2024, chunquedong
// Licensed under the Academic Free License version 3.0
//
package sric.compiler.backend;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import sric.compiler.ast.AstNode;
import sric.compiler.ast.Expr;
import sric.compiler.ast.Expr.*;
import sric.compiler.ast.Stmt;
import sric.compiler.ast.Stmt.*;
import sric.compiler.ast.Type;
import sric.compiler.CompilePass;
import sric.compiler.CompilerLog;
import sric.compiler.ast.AstNode.*;
import sric.compiler.ast.Buildin;
import sric.compiler.ast.FConst;
import sric.compiler.ast.SModule;
import sric.compiler.ast.SModule.Depend;
import sric.compiler.ast.Token.TokenKind;
import sric.compiler.ast.Type.*;

/**
 *
 * @author yangjiandong
 */
public class CppGenerator extends BaseGenerator {
    
    public boolean headMode = true;
    private SModule module;
    
    private HashMap<TypeDef, Integer> emitState = new HashMap<>();
    
    public CppGenerator(CompilerLog log, String file, boolean headMode) throws IOException {
        super(log, file);
        this.headMode = headMode;
    }
    
    public CppGenerator(CompilerLog log, PrintStream writer) {
        super(log, writer);
    }
    
    public void run(SModule module) {
        this.module = module;
        if (headMode) {
            String marcoName = module.name.toUpperCase()+"_H_";
            print("#ifndef ").print(marcoName).newLine();
            print("#define ").print(marcoName).newLine();

            newLine();
            print("#include \"sc_runtime.h\"").newLine();
            
            for (Depend d : module.depends) {
                print("#include \"");
                print(d.name).print(".h");
                print("\"").newLine();
            }
            newLine();
            
            /////////////////////////////////////////////////////////////
            for (FileUnit funit : module.fileUnits) {
                for (TypeDef type : funit.typeDefs) {
                    if ((type.flags & FConst.Extern) != 0 && type.comment != null) {
                        for (Comment comment : type.comment.comments) {
                           if (comment.content.startsWith("#")) {
                               print(comment.content);
                               newLine();
                           }
                        }
                    }
                }
            }
            
            /////////////////////////////////////////////////////////////
            print("namespace ");
            print(module.name);
            print(" {").newLine();
            
            this.indent();
            
            //types decleartion
            for (FileUnit funit : module.fileUnits) {
                for (TypeDef type : funit.typeDefs) {
                    if ((type.flags & FConst.ExternC) != 0) {
                        continue;
                    }
                    
                    if (type instanceof StructDef sd) {
                        printGenericParamDefs(sd.generiParamDefs);
                    }
                    print("struct ");
                    print(type.name).print(";").newLine();
                }
            }
            
            this.unindent();
            newLine();
            print("} //ns").newLine();
            
            /////////////////////////////////////////////////////////////
            
            print("extern \"C\" {");
            this.indent();
            
            for (FileUnit funit : module.fileUnits) {
                for (TypeDef type : funit.typeDefs) {
                    if ((type.flags & FConst.ExternC) != 0) {
                        print("struct ");
                        print(type.name).print(";").newLine();
                    }
                }
                for (FuncDef f : funit.funcDefs) {
                    if ((f.flags & FConst.ExternC) != 0) {
                        printFunc(f, false);
                    }
                }
                for (FieldDef f : funit.fieldDefs) {
                    if ((f.flags & FConst.ExternC) != 0) {
                        visitField(f);
                    }
                }
            }
            
            this.unindent();
            newLine();
            print("} //ns").newLine();

            /////////////////////////////////////////////////////////////
            print("namespace ");
            print(module.name);
            print(" {").newLine();
            
            this.indent();
            
            module.walkChildren(this);

            this.unindent();
            
            newLine();
            print("} //ns").newLine();
            
            newLine();
            print("#endif //");
            print(marcoName).newLine();
        }
        else {
            print("#include \"");
            print(module.name).print(".h");
            print("\"").newLine();
            
            newLine();
            
            module.walkChildren(this);
            
            newLine();
        }
    }

    private void printType(Type type) {
        if (type == null) {
            print("auto");
            return;
        }
        
        if (type.isImutable) {
            print("const ");
        }
        
        switch (type.id.name) {
            case "Void":
                print("void");
                return;
            case "Int":
                NumInfo intType = (NumInfo)type.detail;
                if (intType.size == 8 && intType.isUnsigned == false) {
                    print("char");
                }
                else {
                    if (intType.isUnsigned) {
                        print("u");
                    }
                    print("int"+intType.size+"_t");
                }
                return;
            case "Float":
                NumInfo floatType = (NumInfo)type.detail;
                if (floatType.size == 64) {
                    print("double");
                }
                else {
                    print("float");
                }
                return;
            case "Bool":
                print("bool");
                return;
            case Buildin.pointerTypeName:
                PointerInfo pt = (PointerInfo)type.detail;
                if (pt.pointerAttr == Type.PointerAttr.raw) {
                    printType(type.genericArgs.get(0));
                    print("*");
                }
                else {
                    if (pt.pointerAttr == Type.PointerAttr.own) {
                        print("sric::OwnPtr");
                    }
                    else if (pt.pointerAttr == Type.PointerAttr.ref) {
                        print("sric::RefPtr");
                    }
//                    else if (pt.pointerAttr == Type.PointerAttr.weak) {
//                        print("sric::WeakPtr");
//                    }
                    print("<");
                    printType(type.genericArgs.get(0));
                    print(">");
                }
                return;
            case Buildin.arrayTypeName:
                ArrayInfo arrayType = (ArrayInfo)type.detail;
                printType(type.genericArgs.get(0));
                print("[");
                this.visit(arrayType.sizeExpr);
                print("]");
                return;
            case Buildin.funcTypeName:
                FuncInfo ft = (FuncInfo)type.detail;
                print("std::function<");
                printType(ft.prototype.returnType);
                print("(");
                if (ft.prototype.paramDefs != null) {
                    for (ParamDef p : ft.prototype.paramDefs) {
                        printType(p.paramType);
                    }
                }
                print(")>");
                return;
        }
        
        printIdExpr(type.id);
        
        if (type.genericArgs != null) {
            print("<");
            int i= 0;
            for (Type p : type.genericArgs) {
                if (i > 0) {
                    print(", ");
                }
                printType(p);
                ++i;
            }
            print(">");
        }
    }

    private void printIdExpr(IdExpr id) {
        String ns = id.getNamespaceName();
        if (ns != null) {
            print(ns);
            print("::");
        }
        else {
            if (id.resolvedDef instanceof TopLevelDef td) {
                if ((td.flags & FConst.ExternC) == 0 && td.parent instanceof FileUnit fu) {
                    print(fu.module.name);
                    print("::");
                }
            }
        }
        print(id.name);
    }

    @Override
    public void visitUnit(AstNode.FileUnit v) {
        v.walkChildren(this);
    }

    @Override
    public void visitField(AstNode.FieldDef v) {
        if (v.isLocalVar) {
            printLocalFieldDefAsExpr(v);
            print(";").newLine();
        }
        
        if (headMode && v.parent instanceof FileUnit) {
            print("extern ");
        }
        if (headMode) {
            printLocalFieldDefAsExpr(v);
            print(";").newLine();
        }
        else if ((v.flags & FConst.Static) != 0) {
            printLocalFieldDefAsExpr(v);
            print(";").newLine();
        }
    }
    
    void printLocalFieldDefAsExpr(AstNode.FieldDef v) {
        boolean isImpl = implMode();
        boolean isStatic = (v.flags & FConst.Static) != 0;
        if (v.parent instanceof FileUnit) {
            isStatic = true;
        }
        printType(v.fieldType);
        print(" ");
        if (isStatic && isImpl && !v.isLocalVar) {
            print(((StructDef)v.parent).name);
            print("::");
        }
        print(v.name);
        
        boolean init = false;
        if (v.isLocalVar) {
            init = true;
        }
        else if (isStatic && isImpl) {
            init = true;
        }
        else if (!isStatic && !isImpl) {
            init = true;
        }
        
        if (init && v.initExpr != null) {
            print(" = ");
            this.visit(v.initExpr);
        }
    }
    
    private boolean implMode() {
        return !headMode;
    }
    
    private void printGenericParamDefs(ArrayList<GenericParamDef> generiParamDefs) {
        if (generiParamDefs != null) {
            print("template ");
            print("<");
            int i = 0;
            for (var gp : generiParamDefs) {
                if (i > 0) print(", ");
                print("typename ");
                print(gp.name);
                ++i;
            }
            print(">").newLine();
        }
    }
    
    private boolean isEntryPoint(AstNode.FuncDef v) {
        if (v.parent instanceof FileUnit &&  v.name.equals("main")) {
            return true;
        }
        return false;
    }
    
    private void printFunc(AstNode.FuncDef v, boolean isOperator) {
        boolean inlined = (v.flags & FConst.Inline) != 0 || v.generiParamDefs != null;
        if (v.parent instanceof StructDef sd) {
            if (sd.generiParamDefs != null) {
                inlined = true;
            }
        }
        if (implMode()) {
            if (v.code == null || inlined) {
                return;
            }
        }
        
        newLine();
        
        printGenericParamDefs(v.generiParamDefs);
        
        if ((v.flags & FConst.Virtual) != 0 || (v.flags & FConst.Abstract) != 0) {
            print("virtual ");
        }
        
//        if ((v.flags & FConst.Extern) != 0) {
//            print("extern ");
//        }
        
        printType(v.prototype.returnType);
        print(" ");
        if (implMode()) {
            if (v.parent instanceof TypeDef t) {
                if (t.parent instanceof FileUnit fu) {
                    print(fu.module.name).print("::");
                }
                print(t.name).print("::");
            }
            else if (v.parent instanceof FileUnit fu) {
                if (!isEntryPoint(v)) {
                    print(fu.module.name).print("::");
                }
            }
        }
        
        if (isOperator) {
            print("operator");
            switch (v.name) {
                case "plus":
                    print("+");
                    break;
//                case "set":
//                    print("[]");
//                    break;
                case "get":
                    print("[]");
                    break;
                case "minus":
                    print("-");
                    break;
                case "mult":
                    print("-");
                    break;
                case "div":
                    print("-");
                    break;
                case "compare":
                    print("<=>");
                    break;
                default:
                    break;
            }
        }
        else {
            print(v.name);
        }
        
        printFuncPrototype(v.prototype, false);
        
        if (v.code == null) {
            if ((v.flags & FConst.Abstract) != 0) {
                print(" = 0");
            }
            print(";");
        }
        else {
            if (implMode() || inlined) {
                this.visit(v.code);
            }
            else {
                print(";");
            }
        }
    }
    
    @Override
    public void visitFunc(AstNode.FuncDef v) {
        if ((v.flags & FConst.ExternC) != 0) {
            return;
        }
        if (isEntryPoint(v) && headMode) {
            return;
        }
        
        printFunc(v, false);
        if ((v.flags & FConst.Operator) != 0 && !v.name.equals("set")) {
            printFunc(v, true);
        }
    }
    
    private void printFuncPrototype(FuncPrototype prototype, boolean isLambda) {
        print("(");
        if (prototype != null && prototype.paramDefs != null) {
            int i = 0;
            for (ParamDef p : prototype.paramDefs) {
                if (i > 0) {
                    print(", ");
                }
                if (p.paramType.isVarArgType()) {
                    print("...");
                }
                else {
                    printType(p.paramType);
                    print(" ");
                    print(p.name);
                }
                if (p.defualtValue != null) {
                    print(" = ");
                    this.visit(p.defualtValue);
                }
                ++i;
            }
        }
        print(")");
        
        if (isLambda && prototype != null) {
            if (prototype.returnType != null && !prototype.returnType.isVoid()) {
                print("->");
                printType(prototype.returnType);
            }
        }
    }

    @Override
    public void visitTypeDef(TypeDef v) {
        if ((v.flags & FConst.ExternC) != 0 || (v.flags & FConst.Extern) != 0) {
            return;
        }
        
        if (headMode) {
            //Topo sort
            if (this.emitState.get(v) != null) {
                int state = this.emitState.get(v);
                if (state == 2) {
                    return;
                }
                err("Cyclic dependency", v.loc);
                return;
            }
            this.emitState.put(v, 1);
            if (v instanceof StructDef sd) {
                if (sd.inheritances != null) {
                    for (Type t : sd.inheritances) {
                        if (t.id.resolvedDef != null && t.id.resolvedDef instanceof TypeDef td) {
                            if (td.parent != null && ((FileUnit)td.parent).module == this.module) {
                                this.visitTypeDef(td);
                            }
                        }
                    }
                }
                for (FieldDef f : sd.fieldDefs) {
                    if (!f.fieldType.isPointerType() && f.fieldType.id.resolvedDef != null && f.fieldType.id.resolvedDef instanceof TypeDef td) {
                        if (td.parent != null && td.parent instanceof FileUnit unit) {
                            if (unit.module == this.module) {
                                this.visitTypeDef(td);
                            }
                        }
                    }
                }
            }
            this.emitState.put(v, 2);
        }
        
        if (implMode()) {
            v.walkChildren(this);
            return;
        }
        
        newLine();
        
        if (v instanceof EnumDef edef) {
            print("enum class ");
            print(v.name);
            print(" {").newLine();
            indent();

            int i = 0;
            for (FieldDef f : edef.enumDefs) {
                if (i > 0) {
                    print(",").newLine();
                }
                print(f.name);
                if (f.initExpr != null) {
                    print(" = ");
                    this.visit(f.initExpr);
                    print(";");
                }
                ++i;
            }
            newLine();

            unindent();
            print("};").newLine();
            return;
        }
        
        if (v instanceof StructDef sd) {
            printGenericParamDefs(sd.generiParamDefs);
        }
        
        print("struct ");
        print(v.name);
        
        if (v instanceof StructDef sd) {
            if (sd.inheritances != null) {
                int i = 0;
                for (Type inh : sd.inheritances) {
                    if (i == 0) print(" : ");
                    else print(", ");
                    print("public ");
                    printType(inh);
                }
            }
        }
        
        print(" {").newLine();
        indent();
        
        v.walkChildren(this);
        
        unindent();
        newLine();
        print("};").newLine();
    }

    @Override
    public void visitStmt(Stmt v) {
        if (v instanceof Block bs) {
            print("{").newLine();
            indent();
            bs.walkChildren(this);
            unindent();
            print("}").newLine();
        }
        else if (v instanceof IfStmt ifs) {
            print("if (");
            this.visit(ifs.condition);
            print(") ");
            this.visit(ifs.block);
            if (ifs.elseBlock != null) {
                print("else ");
                this.visit(ifs.elseBlock);
            }
        }
        else if (v instanceof LocalDefStmt e) {
            this.visit(e.fieldDef);
        }
        else if (v instanceof WhileStmt whiles) {
            print("while (");
            this.visit(whiles.condition);
            print(") ");
            this.visit(whiles.block);
        }
        else if (v instanceof ForStmt fors) {
            print("for (");
            if (fors.init != null) {
                if (fors.init instanceof LocalDefStmt varDef) {
                    printLocalFieldDefAsExpr(varDef.fieldDef);
                }
                else if (fors.init instanceof ExprStmt s) {
                    this.visit(s.expr);
                }
                else {
                    err("Unsupport for init stmt", fors.init.loc);
                }
            }
            print("; ");
            
            if (fors.condition != null) {
                this.visit(fors.condition);
            }
            print("; ");
            
            if (fors.update != null) {
                this.visit(fors.update);
            }
            print(") ");
            this.visit(fors.block);
        }
        else if (v instanceof SwitchStmt switchs) {
            print("switch (");
            this.visit(switchs.condition);
            print(") {").newLine();
            
            this.indent();
            
            for (CaseBlock cb : switchs.cases) {
                this.unindent();
                print("case ");
                this.visit(cb.caseExpr);
                print(":").newLine();
                this.indent();
                
                this.visit(cb.block);
                
                if (!cb.fallthrough) {
                    print("break;").newLine();
                }
            }
            
            if (switchs.defaultBlock != null) {
                this.unindent();
                print("default:").newLine();
                this.indent();
                this.visit(switchs.defaultBlock);
            }
 
            this.unindent();
            print("}").newLine();
        }
        else if (v instanceof ExprStmt exprs) {
            this.visit(exprs.expr);
            print(";").newLine();
        }
        else if (v instanceof JumpStmt jumps) {
            print(jumps.opToken.symbol).print(";").newLine();
        }
        else if (v instanceof UnsafeBlock bs) {
            print("/*unsafe*/ ");
            this.visit(bs.block);
        }
        else if (v instanceof ReturnStmt rets) {
            if (rets.expr != null) {
                print("return ");
                this.visit(rets.expr);
                print(";").newLine();
            }
            else {
                print("return;");
            }
        }
        else {
            err("Unkown stmt:"+v, v.loc);
        }
    }

    @Override
    public void visitExpr(Expr v) {
        boolean isPrimitive = false;
        if (v.isStmt || v instanceof IdExpr || v instanceof LiteralExpr || v instanceof CallExpr || v instanceof AccessExpr) {
            isPrimitive = true;
        }
        else {
            print("(");
        }
        
        if (v.implicitTypeConvert != null) {
            print("sric::").print(v.implicitTypeConvert).print("(");
        }
        
        if (v instanceof IdExpr e) {
            this.printIdExpr(e);
        }
        else if (v instanceof AccessExpr e) {
            this.visit(e.target);
            if (e.target.resolvedType.isPointerType()) {
                print("->");
            }
            else {
                print(".");
            }
            print(e.name);
        }
        else if (v instanceof LiteralExpr e) {
            printLiteral(e);
        }
        else if (v instanceof BinaryExpr e) {
            if (e.opToken == TokenKind.asKeyword) {
                print("cast_as<");
                printType(e.rhs.resolvedType);
                print(" >(");
                this.visit(e.lhs);
                print(")");
            }
            else if (e.opToken == TokenKind.isKeyword) {
                print("type_is<");
                printType(e.rhs.resolvedType);
                print(" >(");
                this.visit(e.lhs);
                print(") != nullptr");
            }
            //index set operator: a[i] = b
            else if (e.opToken == TokenKind.assign && e.lhs instanceof IndexExpr iexpr) {
                this.visit(iexpr.target);
                print(".set(");
                this.visit(iexpr.index);
                print(", ");
                this.visit(e.rhs);
                print(")");
            }
            else {
                if (e.operatorName !=  null) {
                    this.visit(e.lhs);
                    print(".").print(e.operatorName).print("(");
                    this.visit(e.rhs);
                    print(")");
                }
                else {
                    this.visit(e.lhs);
                    print(" ");
                    print(e.opToken.symbol);
                    print(" ");
                    this.visit(e.rhs);
                }
            }
        }
        else if (v instanceof CallExpr e) {
            this.visit(e.target);
            print("(");
            if (e.args != null) {
                int i = 0;
                for (CallArg t : e.args) {
                    if (i > 0) print(", ");
                    this.visit(t.argExpr);
                    ++i;
                }
            }
            print(")");
        }
        else if (v instanceof UnaryExpr e) {
            if (e.opToken == TokenKind.amp) {
                print("sric::addressOf(");
                this.visit(e.operand);
                print(")");
            }
            else {
                print(e.opToken.symbol);
                this.visit(e.operand);
            }
        }
        else if (v instanceof TypeExpr e) {
            this.printType(e.type);
        }
        else if (v instanceof IndexExpr e) {
            this.visit(e.target);
            print(".get(");
            this.visit(e.index);
            print(")");
        }
        else if (v instanceof GenericInstance e) {
            this.visit(e.target);
            print("<");
            int i = 0;
            for (Type t : e.genericArgs) {
                if (i > 0) print(", ");
                this.printType(t);
                ++i;
            }
            print(" >");
        }
        else if (v instanceof IfExpr e) {
            this.visit(e.condition);
            print("?");
            this.visit(e.trueExpr);
            print(":");
            this.visit(e.falseExpr);
        }
        else if (v instanceof InitBlockExpr e) {
            printInitBlockExpr(e);
        }
        else if (v instanceof ClosureExpr e) {
            printClosureExpr(e);
        }
        else if (v instanceof OptionalExpr e) {
            this.visit(e.operand);
        }
        else {
            err("Unkown expr:"+v, v.loc);
        }
        
        if (v.implicitTypeConvert != null) {
            print(")");
        }
        
        if (!isPrimitive) {
            print(")");
        }
    }
    
    void printLiteral(LiteralExpr e) {
        if (e.value == null) {
            if (e.nullPtrType != null) {
                print("sric::OwnPtr<");
                printType(e.nullPtrType.genericArgs.get(0));
                print(">()");
            }
            else {
                print("nullptr");
            }
        }
        else if (e.value instanceof Long li) {
            print(li.toString()).print("LL");
        }
        else if (e.value instanceof Double li) {
            print(li.toString());
        }
        else if (e.value instanceof Boolean li) {
            print(li.toString());
        }
        else if (e.value instanceof String li) {
            print("\"");
            for (int i=0; i<li.length(); ++i) {
                char c = li.charAt(i);
                printChar(c);
            }
            print("\"");
        }
    }

    void printChar(char c) {
        switch (c) {
            case '\b':
                print("\\b");
                break;
            case '\n':
                print("\\n");
                break;
            case '\r':
                print("\\r");
                break;
            case '\t':
                print("\\t");
                break;
            case '\"':
                print("\\\"");
                break;
            case '\'':
                print("\\\'");
                break;
            case '\\':
                writer.print('\\');
                break;
            default:
                writer.print(c);
        }
    }
    
    void printInitBlockExpr(InitBlockExpr e) {
        if (!e.target.isResolved()) {
            return;
        }
        if (!e.target.resolvedType.isMetaType()) {
            this.visit(e.target);
        }
        if (e.args != null) {
            int i = 0;
            print("{");
            for (CallArg t : e.args) {
                if (i > 0)
                    print(",");
                if (t.name != null) {
                    print(t.name);
                    print(" = ");
                }
                this.visit(t.argExpr);
                ++i;
            }
            print("}");
        }
    }
    
    void printClosureExpr(ClosureExpr expr) {
        print("[");
        
//        int i = 0;
//        if (expr.defaultCapture != null) {
//            print(expr.defaultCapture.symbol);
//            ++i;
//        }
//        
//        for (Expr t : expr.captures) {
//            if (i > 0) print(", ");
//            this.visit(t);
//            ++i;
//        }
        print("]");
        
        this.printFuncPrototype(expr.prototype, true);
        
        this.visit(expr.code);
    }
}