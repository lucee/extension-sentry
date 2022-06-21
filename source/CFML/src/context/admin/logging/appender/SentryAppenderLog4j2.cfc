component extends="Appender" {
	fields=[

		field(
			"Sentry - DSN","dsn",
			"",
			false,
			"Sentry Data Source Name (DSN). This setting is optional and can also be set in the enviroment instead (https://docs.sentry.io/clients/java/config/##configuration-methods).",
			"text")
	];
	public array function getCustomFields() {
		if(!isNull(form._name)) {
			var fields=duplicate(variables.fields);
			loop array=fields index="local.i" item="local.field" {
				if(field.getName() EQ "Path") {
					local.dv=field.getDefaultValue();
					if(right(dv,1) NEQ "/" and right(dv,1) NEQ "\") {
						dv&="/";
					}
					field.setDefaultValue(dv&form._name&".log");
				}
			}
		}
		return fields;
	}
    
    public string function getClass() {
		return "{class}";
    }
    
    public string function getBundleName() {
		return "{bundlename}";
    }
    
    /*public string function getBundleVersion() {
		return "{bundleversion}";
    }*/
    
    public string function getLabel() {
		return "{label}";
    }
    
    public string function getDescription() {
		return "{desc}";
    }
}