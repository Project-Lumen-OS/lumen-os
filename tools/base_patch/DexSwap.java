// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * DexSwap BASE.dex FIX.dex OUT.dex OUTERS API: OUT = BASE with the class OUTER and its nested classes (OUTER$...)
 * replaced by the classes of FIX. FIX may hold nothing else; every other class of BASE is written unchanged.
 * OUTERS is one class or a comma list of them; an entry with a leading + names a class that is new (the base dex must
 * not have it, the fix dex must).
 */
public final class DexSwap {
    public static void main(String[] a) throws Exception {
        if (a.length != 5) throw new IllegalArgumentException("DexSwap BASE.dex FIX.dex OUT.dex Lpkg/Outer;[,+Lpkg/New;] API");
        Opcodes op = Opcodes.forApi(Integer.parseInt(a[4]));
        DexFile base = DexFileFactory.loadDexFile(new File(a[0]), op);
        DexFile fix = DexFileFactory.loadDexFile(new File(a[1]), op);
        List<String> outers = new ArrayList<>();
        Set<String> fresh = new TreeSet<>();
        for (String s : a[3].split(",")) {
            String o = s.startsWith("+") ? s.substring(1) : s;
            if (!o.startsWith("L") || !o.endsWith(";")) throw new IllegalArgumentException("not a class: " + s);
            outers.add(o);
            if (s.startsWith("+")) fresh.add(o);
        }
        Set<String> gone = new TreeSet<>(), added = new TreeSet<>();
        DexPool pool = new DexPool(op);
        int kept = 0;
        for (ClassDef c : base.getClasses()) {
            if (owner(outers, c.getType()) != null) {
                gone.add(c.getType());
            } else {
                pool.internClass(c);
                kept++;
            }
        }
        for (ClassDef c : fix.getClasses()) {
            if (owner(outers, c.getType()) == null) {
                throw new IllegalStateException("the fix dex holds " + c.getType() + ", outside " + String.join(",", outers));
            }
            added.add(c.getType());
            pool.internClass(c);
        }
        for (String o : outers) {
            if (gone.contains(o) == fresh.contains(o) || !added.contains(o)) {
                throw new IllegalStateException(o + (fresh.contains(o) ? " is in the base dex or missing in the fix dex"
                        : " missing in the base or the fix dex"));
            }
        }
        pool.writeTo(new FileDataStore(new File(a[2])));
        System.out.println("kept " + kept + " classes, replaced " + gone + " with " + added);
    }

    /** The entry of outers that type is or is nested in, else null. */
    private static String owner(List<String> outers, String type) {
        for (String o : outers) {
            if (type.equals(o) || type.startsWith(o.substring(0, o.length() - 1) + "$")) return o;
        }
        return null;
    }
}
