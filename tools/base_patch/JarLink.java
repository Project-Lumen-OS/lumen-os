// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * JarLink API OUTSIDE.txt PREFIXES DEX...: every type, method and field that a class of the jar (its dex files) uses and
 * that the jar itself defines resolves there (through superclasses and interfaces of the jar; a hierarchy that leaves the
 * jar ends at the boot class path, unchecked here). A type is the jar's when the jar defines it or it starts with one of
 * PREFIXES (comma list, e.g. Lcom/android/server/hdmi/): such a type must exist. The references of the classes under
 * PREFIXES that leave the jar go to OUTSIDE.txt (holder name type, one per line) for the platform check. Exit 1 when one
 * reference does not resolve.
 */
public final class JarLink {
    private static final Map<String, ClassDef> CLASSES = new HashMap<>();

    public static void main(String[] a) throws Exception {
        Opcodes op = Opcodes.forApi(Integer.parseInt(a[0]));
        String[] prefixes = a[2].split(",");
        for (int i = 3; i < a.length; i++) {
            DexFile d = DexFileFactory.loadDexFile(new File(a[i]), op);
            for (ClassDef c : d.getClasses()) {
                if (CLASSES.put(c.getType(), c) != null) throw new IllegalStateException("defined twice: " + c.getType());
            }
        }
        Set<String> bad = new TreeSet<>(), outside = new TreeSet<>();
        long n = 0;
        for (ClassDef c : CLASSES.values()) {
            boolean watched = under(prefixes, c.getType());
            for (Method m : c.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                for (Instruction ins : impl.getInstructions()) {
                    if (!(ins instanceof ReferenceInstruction)) continue;
                    Reference r = ((ReferenceInstruction) ins).getReference();
                    n++;
                    String where = c.getType() + "." + m.getName();
                    if (r instanceof MethodReference) {
                        MethodReference mr = (MethodReference) r;
                        String key = mr.getName() + ":" + proto(mr);
                        check(bad, outside, watched, prefixes, where, base(mr.getDefiningClass()), key, true);
                    } else if (r instanceof FieldReference) {
                        FieldReference fr = (FieldReference) r;
                        check(bad, outside, watched, prefixes, where, base(fr.getDefiningClass()),
                                fr.getName() + ":" + fr.getType(), false);
                    } else if (r instanceof TypeReference) {
                        String t = base(((TypeReference) r).getType());
                        if (under(prefixes, t) && !CLASSES.containsKey(t)) bad.add(where + " -> type " + t);
                    }
                }
            }
        }
        try (PrintWriter w = new PrintWriter(a[1], "UTF-8")) {
            for (String s : outside) w.println(s);
        }
        System.out.println("JarLink: " + CLASSES.size() + " classes, " + n + " references, " + bad.size() + " unresolved, "
                + outside.size() + " platform references of " + a[2]);
        for (String s : bad) System.out.println("  UNRESOLVED " + s);
        System.exit(bad.isEmpty() ? 0 : 1);
    }

    private static String base(String t) {
        int i = 0;
        while (i < t.length() && t.charAt(i) == '[') i++;
        return t.substring(i);
    }

    private static boolean under(String[] prefixes, String t) {
        for (String p : prefixes) if (!p.isEmpty() && t.startsWith(p)) return true;
        return false;
    }

    private static String proto(MethodReference m) {
        StringBuilder b = new StringBuilder("(");
        for (CharSequence p : m.getParameterTypes()) b.append(p);
        return b.append(')').append(m.getReturnType()).toString();
    }

    private static void check(Set<String> bad, Set<String> outside, boolean watched, String[] prefixes, String where,
                              String holder, String key, boolean method) {
        if (!holder.startsWith("L")) return;
        if (!CLASSES.containsKey(holder)) {
            if (under(prefixes, holder)) bad.add(where + " -> " + holder + "." + key + " (no such class)");
            else if (watched) outside.add(holder + " " + key.replace(":", " "));
            return;
        }
        Deque<String> todo = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        todo.add(holder);
        boolean left = false;
        while (!todo.isEmpty()) {
            String t = todo.poll();
            if (!seen.add(t)) continue;
            ClassDef c = CLASSES.get(t);
            if (c == null) {
                left = true;
                continue;
            }
            if (method) {
                for (Method m : c.getMethods()) if ((m.getName() + ":" + proto(m)).equals(key)) return;
            } else {
                for (Field f : c.getFields()) if ((f.getName() + ":" + f.getType()).equals(key)) return;
            }
            if (c.getSuperclass() != null) todo.add(c.getSuperclass());
            todo.addAll(c.getInterfaces());
        }
        if (left) {
            if (watched) outside.add(holder + " " + key.replace(":", " "));
            return;
        }
        bad.add(where + " -> " + holder + "." + key);
    }
}
