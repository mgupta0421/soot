/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Ondrej Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package soot.jimple.spark.pag;
import java.util.*;

import soot.*;
import soot.jimple.*;
import soot.jimple.spark.*;
import soot.jimple.spark.builder.*;
import soot.jimple.spark.internal.*;
import soot.util.*;
import soot.util.queue.*;
import soot.jimple.toolkits.pointer.util.NativeMethodDriver;


/** Part of a pointer assignment graph for a single method.
 * @author Ondrej Lhotak
 */
public abstract class AbstractMethodPAG {
    SootMethod method;
    public SootMethod getMethod() { return method; }
    protected MethodNodeFactory nodeFactory;
    public MethodNodeFactory nodeFactory() { return nodeFactory; }
    public abstract AbstractPAG pag();

    public static AbstractMethodPAG v( AbstractPAG pag, SootMethod m ) {
        AbstractMethodPAG ret = (AbstractMethodPAG) G.v().MethodPAG_methodToPag.get( m );
        if( ret == null ) { 
            if( pag instanceof PAG ) {
                ret = new MethodPAG( (PAG) pag, m );
            } else if( pag instanceof BDDPAG ) {
                ret = new BDDMethodPAG( (BDDPAG) pag, m );
            }
            G.v().MethodPAG_methodToPag.put( m, ret );
        }
        return ret;
    }

    public void build() {
        if( hasBeenBuilt ) return;
        hasBeenBuilt = true;
        if( method.isNative() ) {
            if( pag().getOpts().simulate_natives() ) {
                buildNative();
            }
        } else {
            if( method.isConcrete() && !method.isPhantom() ) {
                buildNormal();
            }
        }
        addMiscEdges();
    }

    protected VarNode parameterize( LocalVarNode vn, Object varNodeParameter ) {
        SootMethod m = vn.getMethod();
        if( m != method && m != null ) throw new RuntimeException( "VarNode "+vn+" with method "+m+" parameterized in method "+method );
        //System.out.println( "parameterizing "+vn+" with "+varNodeParameter );
        return pag().makeContextVarNode( vn, varNodeParameter );
    }
    protected FieldRefNode parameterize( FieldRefNode frn, Object varNodeParameter ) {
        return pag().makeFieldRefNode(
                (VarNode) parameterize( frn.getBase(), varNodeParameter ),
                frn.getField() );
    }
    public Node parameterize( Node n, Object varNodeParameter ) {
        if( varNodeParameter == null ) return n;
        if( n instanceof LocalVarNode ) 
            return parameterize( (LocalVarNode) n, varNodeParameter);
        if( n instanceof FieldRefNode )
            return parameterize( (FieldRefNode) n, varNodeParameter);
        return n;
    }
    /** Adds this method to the main PAG, with all VarNodes parameterized by
     * varNodeParameter. */
    public abstract void addToPAG( Object varNodeParameter );
    public abstract void addInternalEdge( Node src, Node dst );
    public abstract void addInEdge( Node src, Node dst );
    public abstract void addOutEdge( Node src, Node dst );
    protected boolean hasBeenAdded = false;
    protected boolean hasBeenBuilt = false;

    protected void buildNormal() {
        Body b = method.retrieveActiveBody();
        Iterator unitsIt = b.getUnits().iterator();
        while( unitsIt.hasNext() )
        {
            Stmt s = (Stmt) unitsIt.next();
            nodeFactory.handleStmt( s );
        }
    }
    protected void buildNative() {
        ValNode thisNode = null;
        ValNode retNode = null; 
        if( !method.isStatic() ) { 
	    thisNode = (ValNode) nodeFactory.caseThis();
        }
        if( method.getReturnType() instanceof RefLikeType ) {
	    retNode = (ValNode) nodeFactory.caseRet();
	}
        ValNode[] args = new ValNode[ method.getParameterCount() ];
        for( int i = 0; i < method.getParameterCount(); i++ ) {
            if( !( method.getParameterType(i) instanceof RefLikeType ) ) continue;
	    args[i] = (ValNode) nodeFactory.caseParm(i);
        }
        NativeMethodDriver.v().process( method, thisNode, retNode, args );
    }

    protected void addMiscEdges() {
        // Add node for parameter (String[]) in main method
        if( method.getSubSignature().equals( SootMethod.getSubSignature( "main", new SingletonList( ArrayType.v(RefType.v("java.lang.String"), 1) ), VoidType.v() ) ) ) {
            addInEdge( pag().nodeFactory().caseArgv(), nodeFactory.caseParm(0) );
        }

        if( method.getSignature().equals(
                    "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>" ) ) {
            addInEdge( pag().nodeFactory().caseMainThread(), nodeFactory.caseThis() );
            addInEdge( pag().nodeFactory().caseMainThreadGroup(), nodeFactory.caseParm( 0 ) );
        }

        if( method.getSubSignature().equals(
            "java.lang.Class loadClass(java.lang.String)" ) ) {
            SootClass c = method.getDeclaringClass();
outer:      do {
                while( !c.getName().equals( "java.lang.ClassLoader" ) ) {
                    if( !c.hasSuperclass() ) {
                        break outer;
                    }
                    c = c.getSuperclass();
                }
                addInEdge( pag().nodeFactory().caseDefaultClassLoader(),
                        nodeFactory.caseThis() );
                addInEdge( pag().nodeFactory().caseMainClassNameString(),
                        nodeFactory.caseParm(0) );
            } while( false );
        }
    }
}

