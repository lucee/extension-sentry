component extends="Appender" {
	
    fields=[

		field("Threshold","threshold","error",true,
			"All log entries that have a log level that is equal or bigger to the log level defined here are send to Sentry, log entries with smaller log levels are send to the file."
			,"select","trace,info,debug,warn,error,fatal")
		//,group("qq","Resource used in case log level is below threshold (see above).",3)
		,field("Sentry - DSN","dsn","",false,"Sentry Data Source Name (DSN). This setting is optional and can also be set in the enviroment instead (https://docs.sentry.io/clients/java/config/##configuration-methods).","text")
		,field("File - Path","resourcepath","{lucee-config}/logs/",true,"Path to the file (any virtual filesystem supported) used if we are below the threshold.","text")
		,field("File - Charset","resourcecharset","UTF-8",true,"charset used to write the file (empty == resource charset) used if we are below the threshold.","text")
		,field("File - Max","resourcemaxfiles","10",true,"Maximal amount of Files created, if this number is reached the oldest get destroyed for every new file used if we are below the threshold.","text")
		,field("File - Max Size (in bytes)","resourcemaxfilesize",10*1024*1024,true,"The maxial size of a log file created in bytes used if we are below the threshold.","text")
		,field("File - Stream Timeout (in minutes)","resourcetimeout","1",true,
			"Defines a timeout in minutes of inactivity after which the stream to the log resource will be automatically closed. If set to [0] a new stream is used for every request to the log resource (slow).","select","0,1,2,3,4,5,6")
		
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