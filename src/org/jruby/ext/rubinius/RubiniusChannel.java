/*
 **** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2011 Charles O Nutter <headius@headius.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ext.rubinius;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyFloat;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class RubiniusChannel extends RubyObject {
    private final LinkedBlockingQueue<IRubyObject> queue = new LinkedBlockingQueue();

    public RubiniusChannel(Ruby runtime, RubyClass metaclass) {
        super(runtime, metaclass);
    }

    public static void createChannelClass(Ruby runtime) {
        RubyClass channelClass = runtime
                .getOrCreateModule("Rubinius")
                .defineClassUnder("Channel", runtime.getObject(), new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
                return new RubiniusChannel(runtime, klazz);
            }
        });
        channelClass.setReifiedClass(RubiniusChannel.class);

        channelClass.defineAnnotatedMethods(RubiniusChannel.class);
    }

    @JRubyMethod(name = "new", meta = true)
    public static IRubyObject rbNew(ThreadContext context, IRubyObject channelClass) {
        return new RubiniusChannel(context.runtime, (RubyClass)channelClass);
    }

    @JRubyMethod(name = {"send", "<<"})
    public IRubyObject send(ThreadContext context, IRubyObject value) {
        queue.add(value);
        return context.nil;
    }

    @JRubyMethod
    public IRubyObject receive(ThreadContext context) {
        try {
            return queue.take();
        } catch (InterruptedException ie) {
            return context.runtime.getFalse();
        }
    }
    
    private static final int NANOSECONDS = 1000000;
    
    @JRubyMethod
    public IRubyObject receive_timeout(ThreadContext context, IRubyObject timeout) {
        long time = 0;
        if (timeout instanceof RubyFixnum) {
            time = ((RubyFixnum)timeout).getLongValue() * NANOSECONDS;
        } else if (timeout instanceof RubyFloat) {
            time = (long)(((RubyFloat)timeout).getDoubleValue() * NANOSECONDS);
        } else if (timeout.isNil()) {
            time = -1;
        } else {
            throw context.runtime.newTypeError("expected Fixnum or Float to Channel#receive_timeout");
        }
        
        try {
            if (time == -1) {
                return queue.take();
            } else {
                return queue.poll(time, TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException ie) {
            return context.runtime.getFalse();
        }
    }
    
    @JRubyMethod
    public IRubyObject try_receive(ThreadContext context) {
        IRubyObject result = queue.poll();
        if (result == null) return context.nil;
        return result;
    }
}
