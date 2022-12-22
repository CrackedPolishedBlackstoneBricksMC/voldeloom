EnumOSHelper is a misnamed class in MCP, it should really be a switchmap class (here https://www.benf.org/other/cfr/switch-on-enum.html for info on switchmap classes)

This tends to break resugaring decompilers that want to restore the enum switch. FabricMC's Fernflower breaks, Quiltflower decompiles but isn't very happy about it

I'm very tired of having to dig up weird decompilers to have one that doesn't crash this method so i'll just paste it

    public static File getAppDir(String par0Str) {
      String var1 = System.getProperty("user.home", ".");
      File var2;
      switch(EnumOSHelper.field_90049_a[getOs().ordinal()]) {
      case 1: //linux (os.name contains "linux" or "unix")
      case 2: //solaris (os.name contains "solaris" or "sunos")
        var2 = new File(var1, '.' + par0Str + '/');
        break;
      case 3: //windows (os.name contains "win")
        String var3 = System.getenv("APPDATA");
        if (var3 != null) {
          var2 = new File(var3, "." + par0Str + '/');
        } else {
          var2 = new File(var1, '.' + par0Str + '/');
        }
        break;
      case 4: //macos (os.name contains "mac")
        var2 = new File(var1, "Library/Application Support/" + par0Str);
        break;
      default: //unknown
        var2 = new File(var1, par0Str + '/');
      }
  
      if (!var2.exists() && !var2.mkdirs()) {
        throw new RuntimeException("The working directory could not be created: " + var2);
      } else {
        return var2;
      }
    }